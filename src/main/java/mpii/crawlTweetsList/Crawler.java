package mpii.crawlTweetsList;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import net.sf.json.JSONObject;

/**
 * Hello world!
 *
 */
public class Crawler 
{
	public String filePath="";
	public String userName="";
	public String since="";
	public String until="";
	public String query="";
	
	public String crawl() {
		File theDir = new File(this.filePath);
		// if the directory does not exist, create it
		if (!theDir.exists()) {
		    boolean result = false;
		    try{
		        theDir.mkdir();
		        result = true;
		    } 
		    catch(SecurityException se){
		    	System.out.println("DIR creating error");  
		    }        
		    if(result) {   
		        System.out.println("DIR created");  
		    }
		}else{
			 System.out.println("DIR exists");  
		}
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		List<Date> lDate=null;
		try {
			Date dBegin = sdf.parse(this.since);
			Date dEnd = sdf.parse(this.until);
			lDate = findDates(dBegin, dEnd);  
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
		Calendar c = Calendar.getInstance();
		for (Date date : lDate){
			String startDate= sdf.format(date);
			c.setTime(date);
			c.add(Calendar.DATE, 1);  // number of days to add
			String endDate = sdf.format(c.getTime());
			crawlOneDay(startDate, endDate);
		}
		return "finish";
	}
	public void crawlOneDay(String startDate, String endDate){
		System.out.println(startDate);
		String appendQuery = "";
		if (!this.query.equals("")) {
			appendQuery=appendQuery+this.query;
		}
		if (!this.userName.equals("")) {
			appendQuery=appendQuery+" from:"+this.userName;
		}
		appendQuery=appendQuery+" since:"+startDate;
		appendQuery=appendQuery+" until:"+endDate;
		
		String q="";
		try {
			q = URLEncoder.encode(appendQuery, "UTF-8").replace("+", "%20");
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}
		BufferedWriter bw =null;
		FileWriter writer =null;
		String refreshCursor="";
		String url;
		int countWrong=0;
		try {
			writer = new FileWriter(this.filePath+startDate+".csv", true);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		bw=new BufferedWriter(writer);
		try {
			bw.write("username"+"\t"+"date"+"\t"+"retweets"+"\t"+"favorites"+"\t"+"text"+"\t"+"geo"+"\t"+"mentions"+"\t"+"hashtags"+"\t"+"id"+"\t"+"permalink");
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
		int line=0;
		int retry=0;
		while(true){
			try {
				url = "https://twitter.com/i/search/timeline?f=tweets&q="+q+"&src=typd&max_position="+refreshCursor;
//				System.out.println(url);
				String result=restGet(url);
				//System.out.println(result);
				JSONObject job=JSONObject.fromObject(result);
				refreshCursor=job.getString("min_position");
				String html=job.getString("items_html");
				Document doc = Jsoup.parse(html);
				Elements tweets = doc.select("div.js-stream-tweet");
				if (tweets.size() == 0) {
					if(refreshCursor.equals("")||retry>10){
						System.out.println(url);
						break;
						}
					else{	
						retry++;
						System.out.println("retry times:"+retry);
						continue;
						}
				}
				retry=0;
				for (Element tweet : tweets) {
					line++;
					try{
						String userNameTweet = tweet.select("a.js-user-profile-link").attr("href").replaceAll("/","");
						String txt = tweet.select("p.js-tweet-text").text().replaceAll("[^\\u0000-\\uFFFF]", "").replaceAll("\t","");
						String retweets = tweet.select("span.ProfileTweet-action--retweet span.ProfileTweet-actionCount").attr("data-tweet-stat-count").replaceAll(",", "");
						String favorites = tweet.select("span.ProfileTweet-action--favorite span.ProfileTweet-actionCount").attr("data-tweet-stat-count").replaceAll(",", "");
						String dateMs = tweet.select("small.time span.js-short-timestamp").attr("data-time-ms");
						long lt = new Long(dateMs);				    
						String dateStr = simpleDateFormat.format(new Date(lt));
						String id = tweet.attr("data-tweet-id");
						String permalink = tweet.attr("data-permalink-path");
						Elements hashtags=tweet.select("a.twitter-hashtag.pretty-link");
						String hashtag=processHashtags(hashtags);
						String mentions=processTerms("(@\\w*)", txt);
						String geo = "";
						Elements geoElement = tweet.select("span.Tweet-geo");
						if (geoElement.size() > 0) {
							geo = geoElement.attr("title");
						}
						List<String> list = Arrays.asList(userNameTweet, id, dateStr, retweets,favorites,hashtag,mentions,geo,txt,permalink);
						String tempString="\n"+String.join("\t",list).replaceAll("\n", "");
						bw.write(tempString);
					}catch(Exception e){
						e.printStackTrace();
						countWrong++;
						System.out.println("Tweet fail "+countWrong);
					}
					if(line%200==0)
						System.out.println(line);
				}				
				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		try {
			bw.close();
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	//send a GET rest request
	public String restGet(String url) {
		String result = "";
		DefaultHttpClient client = new DefaultHttpClient();
		HttpClientParams.setCookiePolicy(client.getParams(), CookiePolicy.BROWSER_COMPATIBILITY);
		HttpGet request = new HttpGet(url);
		// System.out.println(url);
		HttpResponse response=null;
		while(true){
			try {
				response = client.execute(request);				
				int code=response.getStatusLine().getStatusCode();
				if(code==200) break;	
				else {
					Thread.sleep(5000);
					System.out.println("here we can't get 200 response "+url);
				}
			} catch (Exception e) {
				e.printStackTrace();
				break;
			}
		}
		BufferedReader rd;
		try {
			rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			String line = "";
			while ((line = rd.readLine()) != null) {
				result = result + line;
			}
			rd.close();
			Thread.sleep(1000);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		request.releaseConnection();
		return result;
	}
	private static String processTerms(String patternS, String tweetText) {
		StringBuilder sb = new StringBuilder();
		Matcher matcher = Pattern.compile(patternS).matcher(tweetText);
		while (matcher.find()) {
			sb.append(matcher.group());
			sb.append(" ");
		}
		
		return sb.toString().trim();
	}
	private static String processHashtags(Elements hashtags) {
		StringBuilder sb = new StringBuilder();
		for(Element hashtag:hashtags){
			sb.append(hashtag.text());
			sb.append(" ");
		}
		
		return sb.toString().trim();
	}	
	public static List<Date> findDates(Date dBegin, Date dEnd)  
	 {  
	  List lDate = new ArrayList();  
	  lDate.add(dBegin);  
	  Calendar calBegin = Calendar.getInstance();  
	  // 使用给定的 Date 设置此 Calendar 的时间  
	  calBegin.setTime(dBegin);  
	  Calendar calEnd = Calendar.getInstance();  
	  // 使用给定的 Date 设置此 Calendar 的时间  
	  calEnd.setTime(dEnd);  
	  // 测试此日期是否在指定日期之后  
	  while (dEnd.after(calBegin.getTime()))  
	  {  
	   // 根据日历的规则，为给定的日历字段添加或减去指定的时间量  
	   calBegin.add(Calendar.DAY_OF_MONTH, 1);  
	   lDate.add(calBegin.getTime());  
	  }  
	  return lDate;  
	 }  
    public static void main( String[] args )
    {
    	
    	if (args == null || args.length == 0) {
			System.err.println("You must pass some parameters. Use \"-h\" to help.");
			System.exit(0);
		} 
    	Crawler cr=new Crawler();
    	
    	for (String parameter : args) {
			String[] parameterSplit = parameter.split("=");
			
			if (parameterSplit[0].equals("username")) {
				cr.userName=parameterSplit[1];
			} else if (parameterSplit[0].equals("since")) {
				cr.since=parameterSplit[1];
			} else if (parameterSplit[0].equals("until")) {
				cr.until=parameterSplit[1];
			} else if (parameterSplit[0].equals("query")) {
				cr.query=parameterSplit[1];
			} else if (parameterSplit[0].equals("filePath")) {
				cr.filePath=parameterSplit[1];
			}
			
		}
    	
    	String result=cr.crawl();
    	System.out.println(result);
        
    }
}
