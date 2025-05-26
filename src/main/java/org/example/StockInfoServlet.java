//Name: Xiao Yan
//Andrew ID: xyan3



/**
 * StockInfoServlet 逻辑概览：
 *
 * 本 Servlet 用于接收 Android 客户端发来的公司查询请求，流程如下：
 *
 * 1. 接收参数：
 *    - companyName：用户查询的公司名
 *    - phoneModel：发起请求的手机型号（用于日志）
 *
 * 2. 第三方 API 调用：(获取的是json格式）
 *    a) 通过 search API 获取该公司对应的 symbol（股票代码）和正式公司名
 *    b) 再通过 price API 获取该 symbol 最近 10 天的股价和成交量
 *
 * 3. 获取当前时间：
 *    - 获取当前系统时间的日期（yyyy-MM-dd）、星期（如 MONDAY）、小时（0~23）
 *
 * 4. MongoDB 操作：
 *    a) 向 stats 集合中记录该 symbol 的访问次数（首次插入 / 否则 requestCount +1）
 *    b) 向 logs 集合中记录一次完整请求日志，包括时间、手机型号、公司名、价格、成交量、耗时等
 *
 * 5. 构造 JSON 响应：
 *    - 将 symbol、name、prices、volumes 返回给前端
 *
 * 6. 错误处理：
 *    - 若任意步骤抛出异常，返回 status: "error" + 错误信息
 *    - 并在 MongoDB logs 中写入失败日志（包含 errorMessage）
 *
 * 工具函数：
 *    - fetchHttp：通用的 GET 请求方法，返回字符串结果
 */


package org.example;

import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.bson.Document;
import org.json.*;

import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class StockInfoServlet extends HttpServlet {

    // 第三方 API Key（你可以改成自己的）
    private final String API_KEY = "eHd8w726j5M7xfD1TP6dhsDSyFu3xLT2";

    // MongoDB Atlas 的连接字符串（你需要改成你自己的）
    private final String MONGO_URI = "mongodb+srv://daisyyanx11:DB12345678@daisy.ualmo.mongodb.net/?retryWrites=true&w=majority&appName=daisy";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // 设置响应内容为 JSON
        resp.setContentType("application/json");
        PrintWriter out = resp.getWriter();

        // 获取 Android 发来的参数
        String companyName = req.getParameter("companyName");
        String phoneModel = req.getParameter("phoneModel");

        System.out.println("Received request for company: " + companyName);

        // 用来记录 API 响应时间
        long startTime = System.currentTimeMillis();

        // 创建最终返回的 JSON 对象
        JSONObject jsonResponse = new JSONObject();

        try (MongoClient mongoClient = MongoClients.create(MONGO_URI)) {

            MongoDatabase database = mongoClient.getDatabase("stockDB");

            // 日志集合：每次请求记录一次日志
            MongoCollection<Document> logCollection = database.getCollection("logs");

            // 公司统计集合：记录公司被查询的次数（可视为“访问量”）
            MongoCollection<Document> statCollection = database.getCollection("stats");

            // 第一步：通过公司名查 symbol（股票代号）
            String searchUrl = "https://financialmodelingprep.com/api/v3/search?query=" +
                    URLEncoder.encode(companyName, StandardCharsets.UTF_8) +
                    "&exchange=NASDAQ&apikey=" + API_KEY;

            // 调用 search API 获取 symbol
            JSONArray searchResults = new JSONArray(fetchHttp(searchUrl));
            if (searchResults.isEmpty()) {
                throw new Exception("No matching company found.");
            }

            JSONObject company = searchResults.getJSONObject(0);
            String symbol = company.getString("symbol");
            String name = company.getString("name");

            // 第二步：查价格和交易量
            String priceUrl = "https://financialmodelingprep.com/api/v3/historical-price-full/" +
                    symbol + "?timeseries=10&apikey=" + API_KEY;

            JSONObject priceJson = new JSONObject(fetchHttp(priceUrl));
            JSONArray history = priceJson.getJSONArray("historical");

            // 解析价格和交易量
            List<String> prices = new ArrayList<>();
            List<String> volumes = new ArrayList<>();
            for (int i = 0; i < history.length(); i++) {
                JSONObject day = history.getJSONObject(i);
                prices.add(String.valueOf(day.getDouble("close")));
                volumes.add(String.valueOf(day.getLong("volume")));
            }

            // 第三步：记录当前时间信息
            LocalDateTime now = LocalDateTime.now();
            String date = now.toLocalDate().toString();     // e.g. 2025-04-01
            String weekday = now.getDayOfWeek().toString(); // e.g. TUESDAY
            int hour = now.getHour();                       // e.g. 15
            long latency = System.currentTimeMillis() - startTime;

            // 第四步：更新公司访问量 stats 集合
            Document statDoc = statCollection.find(Filters.eq("symbol", symbol)).first();
            if (statDoc == null) {
                statCollection.insertOne(new Document("symbol", symbol)
                        .append("name", name)
                        .append("companyName", companyName)
                        .append("requestCount", 1));
            } else {
                statCollection.updateOne(Filters.eq("symbol", symbol),
                        Updates.inc("requestCount", 1));
            }

            // 第五步：记录日志到MongoDB
            Document logDoc = new Document("date", date)
                    .append("weekday", weekday)
                    .append("hour", hour)
                    .append("companyName", companyName)
                    .append("priceList", String.join(",", prices))
                    .append("volumeList", String.join(",", volumes))
                    .append("phoneModel", phoneModel)
                    .append("status", "success")
                    .append("errorMessage", null)
                    .append("apiResponseMs", latency);

            logCollection.insertOne(logDoc);

            // 第六步：构造 JSON 返回结果给安卓
            jsonResponse.put("name", name);
            jsonResponse.put("symbol", symbol);
            jsonResponse.put("prices", prices);
            jsonResponse.put("volumes", volumes);

        } catch (Exception e) {
            // 错误处理：构造错误 JSON
            jsonResponse.put("status", "error");
            jsonResponse.put("errorMessage", e.getMessage());

            // 尝试写入错误日志
            try (MongoClient mongoClient = MongoClients.create(MONGO_URI)) {
                MongoDatabase database = mongoClient.getDatabase("stockDB");
                MongoCollection<Document> logCollection = database.getCollection("logs");

                Document errorLog = new Document("date", LocalDate.now().toString())
                        .append("hour", LocalTime.now().getHour())
                        .append("companyName", companyName)
                        .append("phoneModel", phoneModel)
                        .append("status", "error")
                        .append("errorMessage", e.getMessage());
                logCollection.insertOne(errorLog);
            }
        }

        // 返回 JSON 给前端
        out.print(jsonResponse.toString());
        out.flush();
    }

    // 工具函数：HTTP GET 请求
    private String fetchHttp(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream())
        );
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return response.toString();
    }
}
