/**
 * 这个类暂时还没用到，by Hyman Lee
 */
package com.hyman.imagewall.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.util.Log;


public class HttpDataService {

	private static HttpDataService instance = null;
	
	private int mPage;
	
	private String responseStr;
	
	private HttpDataService(){}
	
	public static HttpDataService getInstance() {
		 if (instance == null) {
			 synchronized (HttpDataService.class) {
				if (instance == null) {
					instance = new HttpDataService();
				}
			}
		 }
		 return instance;
	}
	
}
