package com.example.imageblog;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.checkbox.MaterialCheckBox;

import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {
    private final List<Post> originalPosts; // 전체 데이터 원본
    private final List<Post> posts; // 화면에 표시되는 필터된 리스트
    private static final String TAG = "ImageAdapter";

    public ImageAdapter(List<Post> posts) {
        this.originalPosts = posts == null ? new ArrayList<>() : new ArrayList<>(posts);
        this.posts = posts == null ? new ArrayList<>() : new ArrayList<>(posts);
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        Post post = posts.get(position);
        final int pos = position;

        // 제목과 내용
        holder.textTitle.setText(post.getTitle() == null ? "" : post.getTitle());
        holder.textBody.setText(post.getText() == null ? "" : post.getText());

        // ✅ 발행 시간 한국어 형식으로 표시
        String rawDate = post.getPublishedDate(); // 예: "2025-10-09T21:31:00"
        if (rawDate != null && !rawDate.isEmpty()) {
            String formattedDate = formatDateString(rawDate);
            holder.textDate.setText(formattedDate);
        } else {
            holder.textDate.setText("");
        }

        // 이미지 표시
        String url = post.getImageUrl();
        if (url != null && !url.isEmpty()) {
            Glide.with(holder.imageViewItem.getContext())
                    .load(url)
                    .centerCrop()
                    .into(holder.imageViewItem);
        } else {
            holder.imageViewItem.setImageDrawable(null);
        }

        // 체크박스 상태 바인딩 (recycling 안전하게 처리)
        holder.checkboxDone.setOnCheckedChangeListener(null);
        holder.checkboxDone.setChecked(post.getProcessed());
        holder.checkboxDone.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // ignore if same
            if (post.getProcessed() == isChecked) return;

            // optimistic update
            boolean old = post.getProcessed();
            post.setProcessed(isChecked);
            Log.d(TAG, "Post id=" + post.getId() + " processed=" + isChecked);

            // Background network PATCH
            Context ctx = holder.itemView.getContext();
            new Thread(() -> {
                OkHttpClient client = NetworkClient.getClient(ctx);
                String token;
                try {
                    android.content.SharedPreferences prefs = ctx.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
                    String stored = prefs.getString("token", null);
                    if (stored != null && !stored.isEmpty()) {
                        token = stored.startsWith("Token ") ? stored : ("Token " + stored);
                    } else {
                        String authHelperToken = AuthHelper.getToken(ctx);
                        if (authHelperToken != null && !authHelperToken.isEmpty()) {
                            token = authHelperToken.startsWith("Token ") ? authHelperToken : ("Token " + authHelperToken);
                        } else {
                            token = "Token 4d571c89d156921c3d20cfc59298df353846cae8";
                        }
                    }
                } catch (Exception e) {
                    token = "Token 4d571c89d156921c3d20cfc59298df353846cae8";
                }

                String urlPatch = "https://cwijiq.pythonanywhere.com/api_root/Post/" + post.getId() + "/";
                try {
                    JSONObject json = new JSONObject();
                    json.put("processed", isChecked);
                    RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json; charset=utf-8"));
                    Request req = new Request.Builder()
                            .url(urlPatch)
                            .addHeader("Authorization", token)
                            .patch(body)
                            .build();

                    Response resp = client.newCall(req).execute();
                    int code = resp.code();
                    String respBody = resp.body() != null ? resp.body().string() : "";
                    resp.close();

                    if (resp.isSuccessful()) {
                        // success: optional toast
                        holder.itemView.post(() -> Toast.makeText(ctx, "처리 상태 업데이트됨", Toast.LENGTH_SHORT).show());
                    } else {
                        // rollback on failure
                        Log.w(TAG, "PATCH processed failed: " + code + " body=" + respBody);
                        post.setProcessed(old);
                        // re-bind this item on UI thread to restore checkbox and state
                        holder.itemView.post(() -> notifyItemChanged(pos));
                        holder.itemView.post(() -> Toast.makeText(ctx, "처리 상태 업데이트 실패: HTTP " + code, Toast.LENGTH_LONG).show());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "patch processed exception", e);
                    post.setProcessed(old);
                    holder.itemView.post(() -> notifyItemChanged(pos));
                    holder.itemView.post(() -> Toast.makeText(ctx, "처리 상태 업데이트 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show());
                }
            }).start();
        });

        // 아이템 클릭 시 상세 화면으로 이동
        holder.itemView.setOnClickListener(v -> {
            android.content.Context ctx = v.getContext();
            android.content.Intent intent = new android.content.Intent(ctx, PostDetailActivity.class);
            intent.putExtra("title", post.getTitle() == null ? "" : post.getTitle());
            intent.putExtra("text", post.getText() == null ? "" : post.getText());
            intent.putExtra("published", post.getPublishedDate() == null ? "" : post.getPublishedDate());
            intent.putExtra("image", post.getImageUrl() == null ? "" : post.getImageUrl());
            intent.putExtra("author", post.getAuthor() == null ? "" : post.getAuthor());
            intent.putExtra("id", post.getId());
            // Activity 컨텍스트이면 startActivityForResult로 열어 삭제/수정 후 결과를 받을 수 있게 함
            if (ctx instanceof android.app.Activity) {
                ((android.app.Activity) ctx).startActivityForResult(intent, MainActivate.REQ_VIEW_POST);
            } else {
                ctx.startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return posts == null ? 0 : posts.size();
    }

    // 외부에서 새로운 데이터로 업데이트할 때 사용
    public void updateData(List<Post> newPosts) {
        originalPosts.clear();
        posts.clear();
        if (newPosts != null && !newPosts.isEmpty()) {
            originalPosts.addAll(newPosts);
            posts.addAll(newPosts);
        }
        notifyDataSetChanged();
    }

    // 간단한 텍스트 필터: 제목 또는 본문에 검색어가 포함된 항목만 표시
    public void filter(String query) {
        if (query == null) query = "";
        String q = query.trim().toLowerCase(Locale.getDefault());
        posts.clear();
        if (q.isEmpty()) {
            posts.addAll(originalPosts);
        } else {
            for (Post p : originalPosts) {
                String t = p.getTitle() == null ? "" : p.getTitle();
                String b = p.getText() == null ? "" : p.getText();
                if (t.toLowerCase(Locale.getDefault()).contains(q) || b.toLowerCase(Locale.getDefault()).contains(q)) {
                    posts.add(p);
                }
            }
        }
        notifyDataSetChanged();
    }

    // 날짜 문자열을 "2025년 10월 9일 9:31 오후" 형식으로 변환
    private String formatDateString(String rawDate) {
        // rawDate에 timezone(+09:00 또는 Z) 정보가 붙어있을 수 있으므로 초(second)까지의 부분만 파싱 시도
        if (rawDate == null || rawDate.isEmpty()) return "";
        String trimmed = rawDate;
        try {
            // ISO 8601 형식 예: 2025-10-29T22:23:00+09:00 또는 2025-10-29T22:23:00Z
            // 초(second)까지의 길이는 19자 (yyyy-MM-dd'T'HH:mm:ss)
            if (trimmed.length() > 19 && trimmed.charAt(19) != ' ') {
                trimmed = trimmed.substring(0, 19);
            }
        } catch (Exception e) {
            // 실패해도 원본을 사용하여 파싱 시도
        }

        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy년 M월 d일 h:mm a", Locale.KOREAN);

        try {
            Date date = inputFormat.parse(trimmed);
            if (date == null) {
                Log.w(TAG, "formatDateString: parsed date is null for rawDate='" + rawDate + "'");
                return rawDate;
            }
            return outputFormat.format(date); // 예: "2025년 10월 9일 9:31 오후"
        } catch (ParseException e) {
            Log.w(TAG, "formatDateString: failed to parse date='" + rawDate + "'", e);
            return rawDate; // 파싱 실패 시 원본 문자열 표시
        }
    }

    public static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageViewItem;
        TextView textTitle;
        TextView textDate;
        TextView textBody;
        MaterialCheckBox checkboxDone; // 변경된 타입

        public ImageViewHolder(View itemView) {
            super(itemView);
            imageViewItem = itemView.findViewById(R.id.imageViewItem);
            textTitle = itemView.findViewById(R.id.textTitle);
            textDate = itemView.findViewById(R.id.textDate);
            textBody = itemView.findViewById(R.id.textBody);
            checkboxDone = itemView.findViewById(R.id.checkboxDone); // 초기화
        }
    }
}
