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
import com.mongodb.client.model.Sorts;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.*;

public class LogsServlet extends HttpServlet {

    // MongoDB 连接 URI（替换成你自己的）
    private final String MONGO_URI = "mongodb+srv://daisyyanx11:DB12345678@daisy.ualmo.mongodb.net/?retryWrites=true&w=majority&appName=daisy";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json"); // 设置返回类型为 JSON
        PrintWriter out = resp.getWriter();      // 获取输出流

        JSONArray results = new JSONArray();     // 准备最终返回的 JSON 数组

        try (MongoClient mongoClient = MongoClients.create(MONGO_URI)) {
            MongoDatabase db = mongoClient.getDatabase("stockDB");          // 选择数据库
            MongoCollection<Document> logs = db.getCollection("logs");      // 选择 logs 集合

            // 查询最近 100 条日志，按时间倒序（假设 hour 越大越新）
            FindIterable<Document> docs = logs.find()
                    .sort(Sorts.descending("date", "hour"))
                    .limit(100);

            for (Document doc : docs) {
                results.put(new JSONObject(doc.toJson()));  // 每条转为 JSON 并加入结果中
            }
        }

        out.print(results.toString()); // 输出整个 JSON 数组
        out.flush();
    }
}
