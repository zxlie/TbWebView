package com.baidufe.libs;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import com.baidufe.R;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;

/**
 * 贴吧通用WebView，支持设置cookie、自定义javascript interface
 * 
 * 文档： 工程根目录/doc/index.html
 * 
 * @author zhaoxianlie
 */
@SuppressLint("SetJavaScriptEnabled")
public class TbWebViewActivity extends Activity {
	static public final String TAG_URL = "tag_url";

	/**
	 * 接受从外界动态绑定javascriptInterface，可以绑定多个
	 */
	private static HashMap<String, JavascriptInterface> mJsInterfaces = null;
	private static boolean mEnableJsInterface = true;
	/**
	 * 接受从外界动态设置cookie，可以设置多个域的cookie
	 */
	private static HashMap<String, String> mCookieMap = null;
	private String mUrl = null;
	private WebView mWebView = null;
	private ProgressBar mProgressBar = null;

	private Handler mHandler = new Handler();
	private Runnable mRunnable = new Runnable() {
		@Override
		public void run() {
			refresh();
		}
	};

	/**
	 * 启动WebView，支持设置：cookie、js interface
	 * 
	 * @param context
	 * @param url
	 *            网址
	 * @param cookieMap
	 *            自定义cookie，格式为HashMap<Domain,Cookie>
	 * @param enableJsInterface
	 *            是否需要支持自定义的javascript interface
	 * @param jsInterface
	 *            自定义的javascript interface
	 */
	private static void startActivity(Context context, String url, HashMap<String, String> cookieMap,
			boolean enableJsInterface, HashMap<String, JavascriptInterface> jsInterface) {
		Intent intent = new Intent(context, TbWebViewActivity.class);
		intent.putExtra(TAG_URL, url);
		mCookieMap = cookieMap;
		mJsInterfaces = jsInterface;
		mEnableJsInterface = enableJsInterface;
		if ((context instanceof Activity) == false) {
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		}
		context.startActivity(intent);
	}

	/**
	 * 启动WebView，支持设置：cookie、js interface
	 * 
	 * @param context
	 * @param url
	 *            网址
	 * @param cookieMap
	 *            自定义cookie，格式为HashMap<Domain,Cookie>
	 * @param jsInterface
	 *            自定义的javascript interface
	 */
	public static void startActivity(Context context, String url, HashMap<String, String> cookieMap,
			HashMap<String, JavascriptInterface> jsInterface) {
		startActivity(context, url, cookieMap, true, jsInterface);
	}

	/**
	 * 启动一个不携带cookie、不支持javascript interface的WebView
	 * 
	 * @param context
	 * @param url
	 */
	public static void startActivity(Context context, String url) {
		startActivity(context, url, null, false, null);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tb_webview_activity);

		// 数据初始化
		Intent intent = this.getIntent();
		mUrl = intent.getStringExtra(TAG_URL);

		if (TextUtils.isEmpty(mUrl)) {
			return;
		}
		// 同步cookie
		initCookie();

		mProgressBar = (ProgressBar) findViewById(R.id.webview_progress);
		initWebView();
		mHandler.postDelayed(mRunnable, 500);
	}

	/**
	 * 同步cookie
	 */
	private void initCookie() {
		CookieSyncManager.createInstance(this);
		CookieManager cookieManager = CookieManager.getInstance();
		if (mCookieMap != null && !mCookieMap.isEmpty()) {
			cookieManager.setAcceptCookie(true);
			Iterator<String> it = mCookieMap.keySet().iterator();
			while (it.hasNext()) {
				String domain = it.next();
				cookieManager.setCookie(domain, mCookieMap.get(domain));
			}
		} else {
			cookieManager.removeAllCookie();
		}
		CookieSyncManager.getInstance().sync();
	}

	/**
	 * 给WebView增加js interface，供FE调用
	 */
	private void addJavascriptInterface() {
		if (!mEnableJsInterface) {
			return;
		}
		if (mJsInterfaces == null) {
			mJsInterfaces = new HashMap<String, JavascriptInterface>();
		}
		// 添加一个通用的js interface接口：TbJsBridge
		if (!mJsInterfaces.containsKey("TbJsBridge")) {
			mJsInterfaces.put("TbJsBridge", new JavascriptInterface() {

				@Override
				public Object createJsInterface(Activity activity) {
					return new TbJsBridge(activity);
				}
			});
		}

		// 增加javascript接口的支持
		Iterator<String> it = mJsInterfaces.keySet().iterator();
		while (it.hasNext()) {
			String key = it.next();
			Object jsInterface = mJsInterfaces.get(key).createJsInterface(this);
			mWebView.addJavascriptInterface(jsInterface, key);
		}
	}

	/**
	 * 初始化webview的相关参数
	 * 
	 * @return
	 */
	private void initWebView() {
		try {
			mWebView = (WebView) findViewById(R.id.webview_entity);
			// 启用js功能
			mWebView.getSettings().setJavaScriptEnabled(true);
			// 增加javascript接口的支持
			addJavascriptInterface();
			// 滚动条设置
			mWebView.setHorizontalScrollBarEnabled(false);
			mWebView.setHorizontalScrollbarOverlay(false);
			mWebView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
			// 必须要设置这个，要不然，webview加载页面以后，会被放大，这里的100表示页面按照原来尺寸的100%显示，不缩放
			mWebView.setInitialScale(100);
			// 处理webview中的各种通知、请求事件等
			mWebView.setWebViewClient(new WebViewClient() {
				@Override
				public boolean shouldOverrideUrlLoading(WebView view, String url) {
					if (TextUtils.isEmpty(url)) {
						return false;
					}
					// 通用url跳转规则
					if (TbUrlBridge.overrideUrl(TbWebViewActivity.this, url)) {
						return true;
					} else {
						// 非通用url规则，则用当前webview直接打开
						mUrl = url;
						refresh();
					}
					return super.shouldOverrideUrlLoading(view, url);
				}
			});
			// 处理webview中的js对话框、网站图标、网站title、加载进度等
			mWebView.setWebChromeClient(new WebChromeClient() {
				@Override
				public void onProgressChanged(WebView view, int newProgress) {
					super.onProgressChanged(view, newProgress);
					if (newProgress == 100) {
						mProgressBar.setVisibility(View.GONE);
					}
				}
			});
			// 使webview支持后退
			mWebView.setOnKeyListener(new View.OnKeyListener() {
				@Override
				public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
					if (mWebView.canGoBack() // webview当前可以返回
							&& keyEvent.getAction() == KeyEvent.ACTION_DOWN // 有按键行为
							&& keyCode == KeyEvent.KEYCODE_BACK) { // 按下了后退键
						mWebView.goBack(); // 后退
						return true;
					}
					return false;
				}
			});
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mWebView == null) {
			return;
		}
		// 控制视频音频的，获取焦点播放，失去焦点停止
		mWebView.resumeTimers();
		callHiddenWebViewMethod("onResume");
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mWebView == null) {
			return;
		}
		mWebView.pauseTimers();
		callHiddenWebViewMethod("onPause");
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mHandler != null) {
			mHandler.removeCallbacks(mRunnable);
		}
	}

	/**
	 * 调用WebView本身的一些方法，有视频音频播放的情况下，必须加这个
	 * 
	 * @param name
	 */
	private void callHiddenWebViewMethod(String name) {
		if (mWebView != null) {
			try {
				Method method = WebView.class.getMethod(name);
				method.invoke(mWebView);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	/**
	 * 刷新WebView，重新加载数据
	 */
	public void refresh() {
		if (mWebView != null && URLUtil.isNetworkUrl(mUrl)) {
			mProgressBar.setVisibility(View.VISIBLE);
			mWebView.loadUrl(mUrl);
		}
	}

	/**
	 * 给WebView增加Javascript Interface的时候，在HashMap中加这个就行了，例子：
	 * 
	 * <pre>
	 * 	HashMap<String, JavascriptInterface> jsInterface = new HashMap<String, JavascriptInterface>();
	 * jsInterface.put("TbJsBridge", new JavascriptInterface() {
	 * 
	 * @author zhaoxianlie
	 * @Override public TbJsBridge createJsInterface(Activity activity) {
	 * return new TbJsBridge(activity);
	 * }
	 * });
	 * TbWebviewActivity.startActivity(AboutActivity.this,
	 * "http://www.baidu.com", null, jsInterface);
	 * </pre>
	 */
	public interface JavascriptInterface {
		public Object createJsInterface(Activity activity);
	}
}
