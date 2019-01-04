package com.thr.crawler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.thr.util.DateUtil;
import com.thr.util.DbUtil;
import com.thr.util.PropertiesUtil;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Status;

/**
 * @author Tang Haorong
 * @description 主体类
 */
public class CnBlogCrawler {

    private static Logger logger=Logger.getLogger(CnBlogCrawler.class);

    private static final String URL="http://www.cnblogs.com/";

    private static Connection con=null;

    private static CacheManager manager=null; // cache管理器

    private static Cache cache=null; // cache缓存对象

    /**
     * 解析主页
     */
    private static void parseHomePage(){
        while(true){
            logger.info("开始爬取"+URL+"网页");
            manager= CacheManager.create(PropertiesUtil.getValue("cacheFilePath"));
            cache=manager.getCache("cnblog");
            CloseableHttpClient httpClient= HttpClients.createDefault(); // 获取HttpClient实例
            HttpGet httpget=new HttpGet(URL); // 创建httpget实例
            RequestConfig config=RequestConfig.custom().setSocketTimeout(100000) // 设置读取超时时间
                    .setConnectTimeout(5000)  // 设置连接超时时间
                    .build();
            httpget.setConfig(config);
            CloseableHttpResponse response=null;
            try {
                response=httpClient.execute(httpget);
            } catch (ClientProtocolException e) {
                logger.error(URL+"-ClientProtocolException",e);
            } catch (IOException e) {
                logger.error(URL+"-IOException",e);
            }
            if(response!=null){
                HttpEntity entity=response.getEntity(); // 获取返回实体
                // 判断返回状态是否为200
                if(response.getStatusLine().getStatusCode()==200){
                    String webPageContent=null;
                    try {
                        webPageContent= EntityUtils.toString(entity, "utf-8");
                        parseHomeWebPage(webPageContent);
                    } catch (ParseException e) {
                        logger.error(URL+"-ParseException",e);
                    } catch (IOException e) {
                        logger.error(URL+"-IOException",e);
                    }
                }else{
                    logger.error(URL+"-返回状态非200");
                }
            }else{
                logger.error(URL+"-连接超时");
            }
            try{
                if(response!=null){
                    response.close();
                }
                if(httpClient!=null){
                    httpClient.close();
                }
            }catch(Exception e){
                logger.error(URL+"Exception", e);
            }
            if(cache.getStatus()== Status.STATUS_ALIVE){
                cache.flush(); // 把缓存写入文件
            }
            manager.shutdown();
            try {
                Thread.sleep(1*60*1000); // 每隔10分钟抓取一次网页数据
            } catch (InterruptedException e) {
                logger.error("InterruptedException", e);
            }
            logger.info("结束爬取"+URL+"网页");
        }
    }

    /**
     * 解析首页内容 提取博客link
     * @param webPageContent
     */
    private static void parseHomeWebPage(String webPageContent){
        if("".equals(webPageContent)){
            return;
        }
        Document doc= Jsoup.parse(webPageContent);
        Elements links=doc.select("#post_list .post_item .post_item_body h3 a");
        for(int i=0;i<links.size();i++){
            Element link=links.get(i);
            String url=link.attr("href");
            System.out.println(url);
            if(cache.get(url)!=null){ // 如果缓存中存在就不插入
                logger.info(url+"-缓存中存在");
                continue;
            }
            parseBlogLink(url);
        }

    }

    /**
     * 解析博客链接地址 获取博客内容
     * @param link
     */
    private static void parseBlogLink(String link){
        logger.info("开始爬取"+link+"网页");
        CloseableHttpClient httpClient=HttpClients.createDefault(); // 获取HttpClient实例
        HttpGet httpget=new HttpGet(link); // 创建httpget实例
        RequestConfig config=RequestConfig.custom().setSocketTimeout(100000) // 设置读取超时时间
                .setConnectTimeout(5000)  // 设置连接超时时间
                .build();
        httpget.setConfig(config);
        CloseableHttpResponse response=null;
        try {
            response=httpClient.execute(httpget);
        } catch (ClientProtocolException e) {
            logger.error(URL+"-ClientProtocolException",e);
        } catch (IOException e) {
            logger.error(URL+"-IOException",e);
        }
        if(response!=null){
            HttpEntity entity=response.getEntity(); // 获取返回实体
            // 判断返回状态是否为200
            if(response.getStatusLine().getStatusCode()==200){
                String blogContent=null;
                try {
                    blogContent=EntityUtils.toString(entity, "utf-8");
                    parseBlogPage(blogContent,link);
                } catch (ParseException e) {
                    logger.error(URL+"-ParseException",e);
                } catch (IOException e) {
                    logger.error(URL+"-IOException",e);
                }
            }else{
                logger.error(URL+"-返回状态非200");
            }
        }else{
            logger.error(URL+"-连接超时");
        }
        try{
            if(response!=null){
                response.close();
            }
            if(httpClient!=null){
                httpClient.close();
            }
        }catch(Exception e){
            logger.error(URL+"Exception", e);
        }
        logger.info("结束爬取"+link+"网页");
    }

    /**
     * 解析博客内容，提取有效信息
     * @param blogContent
     * @param link
     */
    private static void parseBlogPage(String blogContent,String link){
        if("".equals(blogContent)){
            return;
        }
        Document doc=Jsoup.parse(blogContent);
        Elements titleElements=doc.select("#cb_post_title_url"); // 获取博客标题
        if(titleElements.size()==0){
            logger.error(link+"-未获取到博客标题");
            return;
        }
        String title=titleElements.get(0).text();
        System.out.println("博客标题："+title);

        Elements contentElements=doc.select("#cnblogs_post_body"); // 获取博客内容
        Elements imgElements=contentElements.select("img"); // 获取所有图片元素
        if(contentElements.size()==0){
            logger.error(link+"-未获取到博客内容");
            return;
        }
        String content=contentElements.get(0).html();
        System.out.println("博客内容："+content);

        List<String> imgUrlList=new LinkedList<String>();
        for(int i=0;i<imgElements.size();i++){
            Element imgEle=imgElements.get(i);
            String url=imgEle.attr("src");
            imgUrlList.add(url);
            System.out.println(url);
        }

        if(imgUrlList.size()>0){
            Map<String,String> replaceImgMap=downLoadImages(imgUrlList);
            String newContent=replaceWebPageImages(content,replaceImgMap);
            content=newContent;
        }

        // 插入数据库
        String sql="insert into t_blog values(null,?,?,now(),?)";
        try {
            PreparedStatement pstmt=con.prepareStatement(sql);
            pstmt.setString(1, title);
            pstmt.setString(2, content);
            pstmt.setString(3, link);
            if(pstmt.executeUpdate()==1){
                logger.info(link+"-成功插入数据库");
                cache.put(new net.sf.ehcache.Element(link, link));
                logger.info(link+"-已加入缓存");
            }else{
                logger.info(link+"-插入数据库失败");
            }
        } catch (SQLException e) {
            logger.error("SQLException",e);
        }
    }

    /**
     * 把原来的网页图片地址换成本地新的
     * @param content
     * @param replaceImgMap
     * @return
     */
    private static String replaceWebPageImages(String content, Map<String, String> replaceImgMap) {
        for(String url:replaceImgMap.keySet()){
            String newPath=replaceImgMap.get(url);
            content=content.replace(url, newPath);
        }
        return content;
    }

    /**
     * 下载图片到本地
     * @param imgUrlList
     * @return
     */
    private static Map<String,String> downLoadImages(List<String> imgUrlList) {
        Map<String,String> replaceImgMap=new HashMap<String,String>();

        RequestConfig config=RequestConfig.custom().setSocketTimeout(10000) // 设置读取超时时间
                .setConnectTimeout(5000)  // 设置连接超时时间
                .build();
        CloseableHttpClient httpClient=HttpClients.createDefault(); // 获取HttpClient实例
        for(int i=0;i<imgUrlList.size();i++){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            String url=imgUrlList.get(i);
            logger.info("开始爬取"+url+"图片");

            CloseableHttpResponse response=null;

            try {
                HttpGet httpget=new HttpGet(url); // 创建httpget实例
                httpget.setConfig(config);
                response=httpClient.execute(httpget);
            } catch (ClientProtocolException e) {
                logger.error(url+"-ClientProtocolException");
            } catch (IOException e) {
                logger.error(url+"-IOException");
            }
            if(response!=null){
                HttpEntity entity=response.getEntity(); // 获取返回实体
                // 判断返回状态是否为200
                if(response.getStatusLine().getStatusCode()==200){
                    try {
                        InputStream inputStream=entity.getContent();
                        String imageType=entity.getContentType().getValue();
                        String urlB=imageType.split("/")[1];
                        String uuid= UUID.randomUUID().toString();
                        String currentDatePath= DateUtil.getCurrentDatePath();
                        String newPath=PropertiesUtil.getValue("imagePath")+currentDatePath+"/"+uuid+"."+urlB;
                        FileUtils.copyToFile(inputStream, new File(PropertiesUtil.getValue("imageFilePath")+currentDatePath+"/"+uuid+"."+urlB));
                        replaceImgMap.put(url, newPath);
                    } catch (UnsupportedOperationException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }else{
                    logger.error("返回状态非200");
                }
            }else{
                logger.error("连接超时");
            }
            try{
                if(response!=null){
                    response.close();
                }
            }catch(Exception e){
                logger.error("Exception", e);
            }
            logger.info("结束爬取"+url+"图片");
        }

        return replaceImgMap;
    }

    public static void start(){
        DbUtil dbUtil=new DbUtil();
        try {
            con=dbUtil.getCon();
        } catch (Exception e) {
            logger.error("创建数据库连接失败", e);
        }
        parseHomePage();
    }

    public static void main(String[] args) {
        start();
    }

}
