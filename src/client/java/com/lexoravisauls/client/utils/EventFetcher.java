package com.lexoravisauls.client.utils; // Поменяй на свой пакет, если нужно

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class EventFetcher {
    // В этих переменных всегда будет лежать самый свежий текст ивентов
    public static String holyWorldEvents = "Загрузка ивентов...";
    public static String funTimeEvents = "Загрузка ивентов...";

    public static void startFetching() {
        // Запускаем в отдельном потоке, чтобы Майнкрафт не зависал во время скачивания
        new Thread(() -> {
            while (true) {
                try {
                    URL url = new URL("http://31.59.104.219/events.json");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");

                    // Таймауты, чтобы клиент не вис, если сервер будет недоступен
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);

                    if (connection.getResponseCode() == 200) {
                        InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);

                        // Читаем JSON файл
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                        // Достаем текст HolyWorld
                        if (json.has("HolyWorld") && json.getAsJsonArray("HolyWorld").size() > 0) {
                            holyWorldEvents = json.getAsJsonArray("HolyWorld").get(0).getAsJsonObject().get("raw_text").getAsString();
                        }

                        // Достаем текст FunTime
                        if (json.has("FunTime") && json.getAsJsonArray("FunTime").size() > 0) {
                            funTimeEvents = json.getAsJsonArray("FunTime").get(0).getAsJsonObject().get("raw_text").getAsString();
                        }

                        reader.close();
                    }
                } catch (Exception e) {
                    System.out.println("Ошибка при скачивании ивентов Lexora: " + e.getMessage());
                }

                // Поток засыпает на 60 секунд (60000 миллисекунд), затем скачивает снова
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}