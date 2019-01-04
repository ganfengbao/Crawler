package com.thr.util;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Tang Haorong
 * @description 时间工具类
 */
public class DateUtil {

    public static String getCurrentDatePath(){
        Date date=new Date();
        SimpleDateFormat sdf=new SimpleDateFormat("yyyy/MM/dd");
        return sdf.format(date);
    }

    public static void main(String[] args) {
        System.out.println(DateUtil.getCurrentDatePath());
    }
}
