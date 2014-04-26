package com.baidufe;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import com.baidufe.libs.TbWebViewActivity;

public class MyActivity extends Activity
{
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		findViewById(R.id.btn).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				TbWebViewActivity.startActivity(MyActivity.this,
				  "http://zhaoxianlie.fe.baidu.com/android/tbwebview.html");
			}
		});
	}
}
