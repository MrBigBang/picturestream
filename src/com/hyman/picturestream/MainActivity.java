package com.hyman.picturestream;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import com.hyman.picturestream.R;
import com.hyman.picturestream.adapter.InternetPictureStreamAdapter;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.widget.GridView;

public class MainActivity extends Activity {
	
	private Context context;
	
	private GridView mImageWall;
	
	private InternetPictureStreamAdapter adapter;
	
//	private List<File> fileList;
	
	private List<String> imageUrlList = null;
	
	private String sdpath;
	
	private int page = 1;
	
	private ProgressDialog mProgressDialog;
	
	ExtractImageUrlTask task = null;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		context = MainActivity.this;
		initDialog();
//		fileList = new ArrayList<File>();
		getSdCardPath();
//		getAllFiles(new File(sdpath));
		
	}


	@Override
	protected void onStart() {
		super.onStart();
		networkManager(this);
		task = new ExtractImageUrlTask();
		task.execute(1);
	}
	
	private void initDialog() {
		mProgressDialog = new ProgressDialog(context);
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
//		mProgressDialog.setIndeterminate(true);
		mProgressDialog.setCancelable(false);
		mProgressDialog.setCanceledOnTouchOutside(false);
		mProgressDialog.setTitle("Loading...");
		mProgressDialog.setMessage("正在加载图片资源...");
//		NumberFormat numberFormat = NumberFormat.getPercentInstance(Locale.CHINA);
//		mProgressDialog.setProgressPercentFormat(numberFormat);
	}


	private void getSdCardPath() {
		if (isSdCardExist()) {
			sdpath = Environment.getExternalStorageDirectory().getAbsolutePath();
		}
	}


	private boolean isSdCardExist() {
		
		//判断SD卡是否存在，并且是否具有读写权限
		return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
	}


	/**
	 * 获得指定目录下图片文件
	 */
/*	private void getAllFiles(File root) {
		File files[] = root.listFiles();
		if (files != null)
			for (File f : files) {
				if (f.isDirectory()) {
					getAllFiles(f);
				} else {
					if (f.getName().indexOf(".png") > 0
							|| f.getName().indexOf(".jpg") > 0
							|| f.getName().indexOf(".jpeg") > 0)
						this.fileList.add(f);
				}
			}
	}*/

	protected void onDestroy() {
		super.onDestroy();
		adapter.cancelAllTasks();// 退出程序时结束所有的下载任务
		task.cancel(true);
	}
	
	
	class ExtractImageUrlTask extends AsyncTask<Integer, Integer, String> {

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			mProgressDialog.show();
		}
		
		@Override
		protected String doInBackground(Integer... params) {
			String response = "";
			try {
				HttpClient httpClient = new DefaultHttpClient();
				HttpGet httpGet = new HttpGet("http://www.topit.me");
				httpGet.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
				httpGet.setHeader("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3");
				httpGet.setHeader("Connection", "keep-alive");
				httpGet.setHeader("Cookie", "PHPSESSID=41lfbk1ugca4gitis80ki09j17; request_url=%2F; is_click=1");
				httpGet.setHeader("host", "www.topit.me");
				httpGet.setHeader("Referer", "http://www.topit.me/event/warmup/welcome/views/index.html");
				httpGet.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:38.0) Gecko/20100101 Firefox/38.0");
				HttpResponse httpResponse = httpClient.execute(httpGet);
				if (httpResponse.getStatusLine().getStatusCode() == 200) {
					HttpEntity entity = httpResponse.getEntity();
					response = EntityUtils.toString(entity, "utf-8");
				}
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
			return response;
		}
		
		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);
		}
		
		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			mProgressDialog.dismiss();
			
			imageUrlList = extractImageUrl(result);
			mImageWall = (GridView) findViewById(R.id.imagewall_gridview);
			if (imageUrlList != null) {
				adapter = new InternetPictureStreamAdapter(context, 0, imageUrlList, mImageWall);
				mImageWall.setAdapter(adapter);
			}
			
			
		}
		
	}
	
	protected List<String> extractImageUrl(String response) {
		if (response == null) return null;
		//匹配<img>标签的正则表达式
		final String imgTagPattern = "<img\\s+(([^=<>\"']+)=([\"']?)([^'\"<>]+)([\"']?)\\s*)+\\s*/?>([^<>]*?</img>)?";
		Pattern pattern = Pattern.compile(imgTagPattern, Pattern.CASE_INSENSITIVE);
		List<String> urlList = new ArrayList<String>();
		Matcher matcher = pattern.matcher(response);
		String imgUrl = "";
		int dataIndex = 0;
		int srcIndex = 0;
		int urlStartIndex = 0;
		while (matcher.find()) {
			try {
				imgUrl = matcher.group(0);//获得<img id="user_d_187965" title="Elaine.L" src="http://i.topitme.com/3/U/3UGFkKU3m.jpg" />
				
				if (!imgUrl.contains("\"")) continue;
				dataIndex = imgUrl.indexOf("data-original");
				srcIndex = imgUrl.indexOf("src");
				if ( dataIndex > 0) {
					
					urlStartIndex = dataIndex + 15;
			
				} else {
					urlStartIndex = srcIndex + 5;
				}
				imgUrl = imgUrl.substring(urlStartIndex);//获得 http://i.topitme.com/3/U/3UGFkKU3m.jpg" />
				
				if (!imgUrl.contains("\"")) continue;
				imgUrl = imgUrl.substring(0, imgUrl.indexOf("\""));//获得 http://i.topitme.com/3/U/3UGFkKU3m.jpg
				
				
				
				
				if (!imgUrl.isEmpty()) {
					urlList.add(imgUrl);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return urlList;
	}
	
	
	private boolean isNetworkAvailable(Activity activity) {
		//获取手机所有连接管理对象（包括对wifi，net等连接的管理）
		ConnectivityManager connManager = (ConnectivityManager) activity.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
		
		if (connManager == null) return false;
		
		//获取NetworkInfo对象
		NetworkInfo info[] = connManager.getAllNetworkInfo();
		
		if (info != null && info.length > 0) {
			for (int i = 0; i < info.length; i++) {
				//判断当前网络状态是否是连接状态
				if (info[i].getState() == NetworkInfo.State.CONNECTED) {
					return true;
				}
			}
		}
		return false;
		
	}
	
	private void networkManager(final Activity activity) {
		if (!isNetworkAvailable(activity)) {
			//如果网络不可用，则弹出对话框，对网络进行设置
			Builder builder = new AlertDialog.Builder(activity)//
			.setTitle("没有可用的网络")//
			.setMessage("是否对网络进行设置？")//
			.setPositiveButton("是", new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int whichButton) {
					Intent intent = null;
					int sdkVersion = android.os.Build.VERSION.SDK_INT;
					if (sdkVersion > 10) {
						intent = new Intent("android.settings.WIFI_SETTINGS");
					} else {
						ComponentName comp = new ComponentName("com.android.settings", "com.android.settings.WirelessSettings");
						intent = new Intent("/");
						intent.setComponent(comp);
						intent.setAction(Intent.ACTION_VIEW);//android.intent.action.VIEW
					}
					activity.startActivityForResult(intent, 0);
				}
			})//
			.setNeutralButton("否", new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int whichButton) {
					dialog.cancel();
				}
			});
			
			builder.show();
		}
	}
}
