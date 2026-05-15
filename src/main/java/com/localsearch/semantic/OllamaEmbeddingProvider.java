package com.localsearch.semantic;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class OllamaEmbeddingProvider
implements EmbeddingProvider
{
    private static final int MAX_TEXT_CHARS = 4000;

    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;

    public OllamaEmbeddingProvider(String baseUrl, String model)
    {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public float[] embed(String text)
    {
        if (text == null || text.isBlank())
        {
            return new float[0];
        }
        String truncated = text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
        try
        {
            String body = "{\"model\":\"" + escapeJson(model) + "\",\"prompt\":\"" + escapeJson(truncated) + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/embeddings"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
            {
                System.err.println("Ollama returned HTTP " + response.statusCode() + " — semantic embedding skipped.");
                return new float[0];
            }
            return parseEmbeddingArray(response.body());
        }
        catch (IOException | InterruptedException e)
        {
            System.err.println("Ollama unreachable (" + e.getMessage() + ") — semantic embedding skipped.");
            return new float[0];
        }
    }

    public static boolean isReachable(String baseUrl)
    {
        try
        {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/api/tags"))
                    .GET()
                    .timeout(Duration.ofSeconds(2))
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    private static float[] parseEmbeddingArray(String json)
    {
        int keyIndex = json.indexOf("\"embedding\"");
        if (keyIndex == -1)
        {
            return new float[0];
        }
        int arrayStart = json.indexOf('[', keyIndex);
        int arrayEnd = json.indexOf(']', arrayStart);
        if (arrayStart == -1 || arrayEnd == -1)
        {
            return new float[0];
        }
        String content = json.substring(arrayStart + 1, arrayEnd).trim();
        if (content.isEmpty())
        {
            return new float[0];
        }
        String[] parts = content.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++)
        {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }

    private static String escapeJson(String text)
    {
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            switch (c)
            {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default ->
                {
                    if (c < 0x20)
                    {
                        sb.append(String.format("\\u%04x", (int) c));
                    }
                    else
                    {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
