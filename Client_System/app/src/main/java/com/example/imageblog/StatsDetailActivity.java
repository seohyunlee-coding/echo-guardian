package com.example.imageblog;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class StatsDetailActivity extends AppCompatActivity {
    private static final String TAG = "StatsDetailActivity";
    public static final String EXTRA_TYPE = "type"; // "separ" or "illegal"

    RecyclerView recyclerView;
    TextView tvTitle;
    StatsAdapter adapter;
    String site_url = "https://cwijiq.pythonanywhere.com";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats_detail);

        recyclerView = findViewById(R.id.stats_recycler);
        tvTitle = findViewById(R.id.stats_title);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new StatsAdapter(new ArrayList<StatsItem>());
        recyclerView.setAdapter(adapter);

        Intent it = getIntent();
        String type = it != null ? it.getStringExtra(EXTRA_TYPE) : "separ";
        if (type == null) type = "separ";

        if ("separ".equals(type)) tvTitle.setText("분리수거 항목 통계");
        else tvTitle.setText("무단 투기 항목 통계");

        fetchStats(type);
    }

    private void fetchStats(String type) {
        OkHttpClient client = NetworkClient.getClient(this);
        String url = site_url + "/api/stats/";
        Request req = new Request.Builder().url(url).get().build();

        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "fetchStats failed", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        Log.w(TAG, "fetchStats response not successful: " + response.code());
                        response.close();
                        return;
                    }
                    String body = response.body() != null ? response.body().string() : null;
                    response.close();
                    if (body == null || body.isEmpty()) return;

                    JSONObject root = new JSONObject(body);
                    JSONObject counts = root.optJSONObject("counts");
                    JSONObject target = counts != null ? counts.optJSONObject(type) : null;

                    final List<StatsItem> items = new ArrayList<>();
                    if (target != null) {
                        Iterator<String> keys = target.keys();
                        while (keys.hasNext()) {
                            String k = keys.next();
                            int v = target.optInt(k, 0);
                            items.add(new StatsItem(k, v));
                        }
                    }

                    runOnUiThread(() -> adapter.update(items));

                } catch (JSONException e) {
                    Log.e(TAG, "fetchStats parse failed", e);
                }
            }
        });
    }
}

