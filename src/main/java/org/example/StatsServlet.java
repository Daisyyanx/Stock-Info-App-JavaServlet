//Name: Xiao Yan
//Andrew ID: xyan3


package org.example;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;
import com.mongodb.client.*;

public class StatsServlet extends HttpServlet {

    private final String MONGO_URI = "\"mongodb+srv://daisyyanx11:DB12345678@daisy.ualmo.mongodb.net/?retryWrites=true&w=majority&appName=daisy"; // MongoDB 地址

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json"); // 设置响应类型为 JSON
        PrintWriter out = resp.getWriter();      // 获取输出流

        JSONArray results = new JSONArray();     // 存储所有统计信息的 JSON 数组

        try (MongoClient mongoClient = MongoClients.create(MONGO_URI)) {
            MongoDatabase db = mongoClient.getDatabase("stockDB");          // 连接数据库
            MongoCollection<Document> stats = db.getCollection("stats");    // 选择 stats 集合

            for (Document doc : stats.find()) {
                results.put(new JSONObject(doc.toJson())); // 每个文档转换为 JSON 添加到结果中
            }
        }

        out.print(results.toString()); // 返回最终 JSON
        out.flush();
    }
}
