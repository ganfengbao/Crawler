package com.thr.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @author Tang Haorong
 * @description 数据库连接工具
 */
public class DbUtil {

    /**
     * 获取连接
     * @return
     * @throws Exception
     */
    public Connection getCon() throws Exception{
        Class.forName(PropertiesUtil.getValue("jdbcName"));
        Connection con= DriverManager.getConnection(PropertiesUtil.getValue("dbUrl"),PropertiesUtil.getValue("dbUserName"),PropertiesUtil.getValue("dbPassword"));
        return con;
    }

    /**
     * 关闭连接
     * @param con
     * @throws SQLException
     */
    public void closeCon(Connection con) throws SQLException {
        if(con!=null){
            con.close();
        }
    }

    /**
     * 连接数据库
     * @param args
     */
    public static void main(String[] args) {
        DbUtil dbUtil=new DbUtil();
        try {
            dbUtil.getCon();
            System.out.println("数据库连接成功！");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("数据库连接失败");
        }
    }
}
