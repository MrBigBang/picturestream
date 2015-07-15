/**
 * ��ȡ���ص�ͼƬ
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
	
	// ��¼�����������ػ�ȴ����ص�����
	private Set<BitmapWorkerTask> taskCollection;
	// ͼƬ���漼���ĺ����࣬���ڻ����������غõ�ͼƬ���ڳ����ڴ�ﵽ�趨ֵʱ��//���������ʹ�õ�ͼƬ�Ƴ�����
	private LruCache<String, Bitmap> mMemoryCache;
	// GridView��ʵ��
	private GridView mImageWall;
	// ��һ�ſɼ�ͼƬ���±�
	private int mFirstVisibleItem;
	// һ���ж�����ͼƬ�ɼ�
	private int mVisibleItemCount;
	// ��¼�Ƿ�մ򿪳������ڽ��������򲻹�����Ļ����������ͼƬ�����⡣
	private boolean isFirstEnter = true;
	List<File> fileList = null;

	public PitureStreamAdapter(Context context, int textViewResourceId,
			List<File> objects, GridView imageWall) {
		super(context, textViewResourceId, objects);
		inflater = LayoutInflater.from(context);
		mImageWall = imageWall;
		fileList = objects;
		taskCollection = new HashSet<BitmapWorkerTask>();
		// ��ȡӦ�ó����������ڴ�
		int maxMemory = (int) Runtime.getRuntime().maxMemory();
		int cacheSize = maxMemory / 8;
		// ����ͼƬ�����СΪ�����������ڴ��1/8
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
		// ��ImageView����һ��Tag����֤�첽����ͼƬʱ��������
		photo.setTag(url.getAbsolutePath());
		setImageView(url.getAbsolutePath(), photo);
		return view;
	}

	/**
	 * ��ImageView����ͼƬ�����ȴ�LruCache��ȡ��ͼƬ�Ļ��棬���õ�ImageView�ϡ����LruCache��û�и�ͼƬ�Ļ��棬
	 * �͸�ImageView����һ��Ĭ��ͼƬ��
	 * 
	 * @param imageUrl
	 *            ͼƬ��URL��ַ��������ΪLruCache�ļ���
	 * @param imageView
	 *            ������ʾͼƬ�Ŀؼ���
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
	 * ��һ��ͼƬ�洢��LruCache�С�
	 * 
	 * @param key
	 *            LruCache�ļ������ﴫ��ͼƬ��URL��ַ��
	 * @param bitmap
	 *            LruCache�ļ������ﴫ������������ص�Bitmap����
	 */
	@SuppressLint("NewApi")
	public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
		if (getBitmapFromMemoryCache(key) == null) {
			mMemoryCache.put(key, bitmap);
		}
	}

	/**
	 * ��LruCache�л�ȡһ��ͼƬ����������ھͷ���null��
	 * 
	 * @param key
	 *            LruCache�ļ������ﴫ��ͼƬ��URL��ַ��
	 * @return ��Ӧ�������Bitmap���󣬻���null��
	 */
	@SuppressLint("NewApi")
	public Bitmap getBitmapFromMemoryCache(String key) {
		return mMemoryCache.get(key);
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		// ����GridView��ֹʱ��ȥ����ͼƬ��GridView����ʱȡ�������������ص�����
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
		// ���ص�����Ӧ����onScrollStateChanged����ã����״ν������ʱonScrollStateChanged��������ã�
		// ���������Ϊ�״ν����������������
		if (isFirstEnter && visibleItemCount > 0) {
			loadBitmaps(firstVisibleItem, visibleItemCount);
			isFirstEnter = false;
		}
	}

	/**
	 * ����Bitmap���󡣴˷�������LruCache�м��������Ļ�пɼ���ImageView��Bitmap����
	 * ��������κ�һ��ImageView��Bitmap�����ڻ����У��ͻῪ���첽�߳�ȥ����ͼƬ��
	 * 
	 * @param firstVisibleItem
	 *            ��һ���ɼ���ImageView���±�
	 * @param visibleItemCount
	 *            ��Ļ���ܹ��ɼ���Ԫ����
	 */
	private void loadBitmaps(int firstVisibleItem, int visibleItemCount) {
		try {
			for (int i = firstVisibleItem; i < firstVisibleItem + visibleItemCount; i++) {
				String imageUrl = fileList.get(i).getAbsolutePath();
				Bitmap bitmap = getBitmapFromMemoryCache(imageUrl);
				if (bitmap == null) {// �������û��
					BitmapWorkerTask task = new BitmapWorkerTask();
					taskCollection.add(task);
					task.execute(imageUrl);// ִ���첽���񣬲�������ص�ͼƬurl��ַ��������sd���ϵ�ͼƬ��
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
	 * ȡ�������������ػ�ȴ����ص�����
	 */
	public void cancelAllTasks() {
		if (taskCollection != null) {
			for (BitmapWorkerTask task : taskCollection) {
				task.cancel(false);
			}
		}
	}

	/**
	 * �첽����ͼƬ������
	 */
	class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {

		/**
		 * ͼƬ��URL��ַ
		 */
		private String imageUrl;

		@Override
		protected Bitmap doInBackground(String... params) {
			imageUrl = params[0];
			// �ں�̨��ʼ����ͼƬ
			Bitmap bitmap = getLoacalBitmap(params[0]);
			if (bitmap != null) {
				// ͼƬ������ɺ󻺴浽LrcCache��
				addBitmapToMemoryCache(params[0], bitmap);
			}
			return bitmap;
		}

		@Override
		protected void onPostExecute(Bitmap bitmap) {
			super.onPostExecute(bitmap);
			// ����Tag�ҵ���Ӧ��ImageView�ؼ��������غõ�ͼƬ��ʾ������
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
			//��������inJustDecodeBoundsΪtrue�����ִ���������󷵻ص�bitmapΪ�գ����ǿ��Ի��ͼƬ��С��Ϣ��
			bitmap = BitmapFactory.decodeStream(new FileInputStream(url), null, opts);
			//�������ű����������ǹ̶��������ţ�ֻ�ø߻��߿�����һ�����ݽ��м��㼴��
			int scale = (int) (opts.outHeight / (float) 200);
			//��Ϊ�����int�ͣ���������ʵ��ֵΪС�������ս����0��������1����֤����ͼƬ����200px��С��200px��
			if (scale <= 0) {
				scale = 1;
			}
			opts.inJustDecodeBounds = false;
			opts.inSampleSize = scale;
			bitmap = BitmapFactory.decodeStream(new FileInputStream(url), null, opts);
			Log.d("------------------------>>>image size", bitmap + "");
			return bitmap; // ����ת��ΪBitmapͼƬ

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

}
