/**
 * 获取本地的图片
 */
package com.hyman.picturestream.adapter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.hyman.picturestream.R;

public class PitureStreamAdapter extends ArrayAdapter<File> implements
		OnScrollListener {

	private LayoutInflater inflater;
	
	// 记录所有正在下载或等待下载的任务。
	private Set<BitmapWorkerTask> taskCollection;
	// 图片缓存技术的核心类，用于缓存所有下载好的图片，在程序内存达到设定值时会//将最少最近使用的图片移除掉。
	private LruCache<String, Bitmap> mMemoryCache;
	// GridView的实例
	private GridView mImageWall;
	// 第一张可见图片的下标
	private int mFirstVisibleItem;
	// 一屏有多少张图片可见
	private int mVisibleItemCount;
	// 记录是否刚打开程序，用于解决进入程序不滚动屏幕，不会下载图片的问题。
	private boolean isFirstEnter = true;
	List<File> fileList = null;

	public PitureStreamAdapter(Context context, int textViewResourceId,
			List<File> objects, GridView imageWall) {
		super(context, textViewResourceId, objects);
		inflater = LayoutInflater.from(context);
		mImageWall = imageWall;
		fileList = objects;
		taskCollection = new HashSet<BitmapWorkerTask>();
		// 获取应用程序最大可用内存
		int maxMemory = (int) Runtime.getRuntime().maxMemory();
		int cacheSize = maxMemory / 8;
		// 设置图片缓存大小为程序最大可用内存的1/8
		mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
			@Override
			protected int sizeOf(String key, Bitmap bitmap) {
				return bitmap.getByteCount();
			}
		};
		mImageWall.setOnScrollListener(this);
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		final File url = getItem(position);
		View view;
		if (convertView == null) {
			view = inflater.inflate(R.layout.gridview_imagewall_item, null);
		} else {
			view = convertView;
		}
		final ImageView photo = (ImageView) view.findViewById(R.id.image);
		// 给ImageView设置一个Tag，保证异步加载图片时不会乱序
		photo.setTag(url.getAbsolutePath());
		setImageView(url.getAbsolutePath(), photo);
		return view;
	}

	/**
	 * 给ImageView设置图片。首先从LruCache中取出图片的缓存，设置到ImageView上。如果LruCache中没有该图片的缓存，
	 * 就给ImageView设置一张默认图片。
	 * 
	 * @param imageUrl
	 *            图片的URL地址，用于作为LruCache的键。
	 * @param imageView
	 *            用于显示图片的控件。
	 */
	private void setImageView(String imageUrl, ImageView imageView) {
		Bitmap bitmap = getBitmapFromMemoryCache(imageUrl);
		if (bitmap != null) {
			imageView.setImageBitmap(bitmap);
		} else {
			bitmap = getLoacalBitmap(imageUrl);
			imageView.setImageResource(R.drawable.empty_img);
		}
	}

	/**
	 * 将一张图片存储到LruCache中。
	 * 
	 * @param key
	 *            LruCache的键，这里传入图片的URL地址。
	 * @param bitmap
	 *            LruCache的键，这里传入从网络上下载的Bitmap对象。
	 */
	@SuppressLint("NewApi")
	public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
		if (getBitmapFromMemoryCache(key) == null) {
			mMemoryCache.put(key, bitmap);
		}
	}

	/**
	 * 从LruCache中获取一张图片，如果不存在就返回null。
	 * 
	 * @param key
	 *            LruCache的键，这里传入图片的URL地址。
	 * @return 对应传入键的Bitmap对象，或者null。
	 */
	@SuppressLint("NewApi")
	public Bitmap getBitmapFromMemoryCache(String key) {
		return mMemoryCache.get(key);
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		// 仅当GridView静止时才去下载图片，GridView滑动时取消所有正在下载的任务
		if (scrollState == SCROLL_STATE_IDLE) {
			loadBitmaps(mFirstVisibleItem, mVisibleItemCount);
		} else {
			cancelAllTasks();
		}
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem,
			int visibleItemCount, int totalItemCount) {
		mFirstVisibleItem = firstVisibleItem;
		mVisibleItemCount = visibleItemCount;
		// 下载的任务应该由onScrollStateChanged里调用，但首次进入程序时onScrollStateChanged并不会调用，
		// 因此在这里为首次进入程序开启下载任务。
		if (isFirstEnter && visibleItemCount > 0) {
			loadBitmaps(firstVisibleItem, visibleItemCount);
			isFirstEnter = false;
		}
	}

	/**
	 * 加载Bitmap对象。此方法会在LruCache中检查所有屏幕中可见的ImageView的Bitmap对象，
	 * 如果发现任何一个ImageView的Bitmap对象不在缓存中，就会开启异步线程去下载图片。
	 * 
	 * @param firstVisibleItem
	 *            第一个可见的ImageView的下标
	 * @param visibleItemCount
	 *            屏幕中总共可见的元素数
	 */
	private void loadBitmaps(int firstVisibleItem, int visibleItemCount) {
		try {
			for (int i = firstVisibleItem; i < firstVisibleItem + visibleItemCount; i++) {
				String imageUrl = fileList.get(i).getAbsolutePath();
				Bitmap bitmap = getBitmapFromMemoryCache(imageUrl);
				if (bitmap == null) {// 如果缓存没有
					BitmapWorkerTask task = new BitmapWorkerTask();
					taskCollection.add(task);
					task.execute(imageUrl);// 执行异步任务，并传入加载的图片url地址（这里是sd卡上的图片）
				} else {
					ImageView imageView = (ImageView) mImageWall.findViewWithTag(imageUrl);
					if (imageView != null && bitmap != null) {
						imageView.setImageBitmap(bitmap);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 取消所有正在下载或等待下载的任务。
	 */
	public void cancelAllTasks() {
		if (taskCollection != null) {
			for (BitmapWorkerTask task : taskCollection) {
				task.cancel(false);
			}
		}
	}

	/**
	 * 异步下载图片的任务。
	 */
	class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {

		/**
		 * 图片的URL地址
		 */
		private String imageUrl;

		@Override
		protected Bitmap doInBackground(String... params) {
			imageUrl = params[0];
			// 在后台开始下载图片
			Bitmap bitmap = getLoacalBitmap(params[0]);
			if (bitmap != null) {
				// 图片下载完成后缓存到LrcCache中
				addBitmapToMemoryCache(params[0], bitmap);
			}
			return bitmap;
		}

		@Override
		protected void onPostExecute(Bitmap bitmap) {
			super.onPostExecute(bitmap);
			// 根据Tag找到相应的ImageView控件，将下载好的图片显示出来。
			ImageView imageView = (ImageView) mImageWall.findViewWithTag(imageUrl);
			if (imageView != null && bitmap != null) {
				imageView.setImageBitmap(bitmap);
			}
			taskCollection.remove(this);
		}

	}

	private Bitmap getLoacalBitmap(String url) {
		
		Bitmap bitmap = null;
		
		try {
//			FileInputStream fis = new FileInputStream(url);
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inJustDecodeBounds = true;
			//由于设置inJustDecodeBounds为true，因此执行下面代码后返回的bitmap为空，但是可以获得图片大小信息等
			bitmap = BitmapFactory.decodeStream(new FileInputStream(url), null, opts);
			//计算缩放比例，由于是固定比例缩放，只用高或者宽其中一个数据进行计算即可
			int scale = (int) (opts.outHeight / (float) 200);
			//因为结果是int型，如果相除后实际值为小数，最终结果是0，将其置1，保证所有图片都是200px或小于200px的
			if (scale <= 0) {
				scale = 1;
			}
			opts.inJustDecodeBounds = false;
			opts.inSampleSize = scale;
			bitmap = BitmapFactory.decodeStream(new FileInputStream(url), null, opts);
			Log.d("------------------------>>>image size", bitmap + "");
			return bitmap; // 把流转化为Bitmap图片

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

}
