package com.example.imageblog;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Spanned;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.HtmlCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivate extends AppCompatActivity {
    private static final String TAG = "MainActivate";
    public static final int REQ_VIEW_POST = 1001; // 상세 보기/편집 요청 코드
    TextView textView;
    TextView tvUsername;
    RecyclerView recyclerView;
    MaterialButton btnLogin; // 툴바의 로그인 버튼
    MaterialButton btnRegister; // 툴바의 회원가입 버튼
    MaterialButton btnLogout; // 로그아웃 버튼
    MaterialButton btnAddPost; // 새 신고 포스트 작성 버튼
    MaterialButton btnSync; // 동기화 버튼
    MaterialButton btnSearch; // 검색 버튼
    EditText etSearch; // 검색 입력창
    ImageAdapter imageAdapter; // 어댑터 필드로 유지
    LinearLayout buttonGroupGuest; // 로그인 전 버튼 그룹
    LinearLayout buttonGroupUser; // 로그인 후 버튼 그룹
    String site_url = "https://cwijiq.pythonanywhere.com"; // 변경된 API 호스트
    Thread fetchThread;
    String lastRawJson = null; // 디버깅용으로 원시 JSON을 저장;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activate_main); // 레이아웃 이름 수정

        // Toolbar를 레이아웃에서 찾아서 지원 액션바로 설정
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                // 기본 타이틀은 숨기고 커스텀 TextView로 대체
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
        }

        // 뷰 초기화
        textView = findViewById(R.id.textView);
        tvUsername = findViewById(R.id.tv_username);
        recyclerView = findViewById(R.id.recyclerView);

        // 검색 뷰 바인딩 (before listeners)
        etSearch = findViewById(R.id.et_search);
        btnSearch = findViewById(R.id.btn_search);

        // 버튼 그룹 초기화
        buttonGroupGuest = findViewById(R.id.button_group_guest);
        buttonGroupUser = findViewById(R.id.button_group_user);

        // 버튼들 초기화
        btnLogin = findViewById(R.id.btn_login);
        btnRegister = findViewById(R.id.btn_register);
        btnLogout = findViewById(R.id.btn_logout);
        btnAddPost = findViewById(R.id.btn_add_post);
        btnSync = findViewById(R.id.btnSync);

        // LinearLayoutManager로 1열 리스트 표시 (요청사항에 따라)
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);

        // 백그라운드에서 토큰을 미리 가져와서 첫 요청 시 인터셉터가 블로킹하는 시간을 줄입니다.
        new Thread(() -> {
            try {
                AuthHelper.getToken(MainActivate.this);
            } catch (Exception e) {
                Log.d(TAG, "pre-warm token failed", e);
            }
        }).start();

        // 버튼 클릭 리스너 설정
        setupButtonListeners();

        // 로그인 상태 확인 및 UI 업데이트
        updateUI();

        // 초기 상태: 자동으로 데이터 로드 시작
        recyclerView.setVisibility(View.GONE);
        textView.setText("데이터를 불러오는 중...");

        // 자동으로 데이터 로드 시작
        startFetch(site_url + "/api/posts");

        Log.d(TAG, "onCreate: 자동 데이터 로드 시작");
    }

    private void setupButtonListeners() {
        // 로그인 버튼
        btnLogin.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivate.this, LoginActivity.class);
            startActivity(intent);
        });

        // 회원가입 버튼
        btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivate.this, RegisterActivity.class);
            startActivity(intent);
        });

        // 로그아웃 버튼
        btnLogout.setOnClickListener(v -> {
            logout();
        });

        // 새 신고 포스트 작성 버튼
        btnAddPost.setOnClickListener(v -> {
            Intent intent = new Intent(this, NewPostActivity.class);
            startActivityForResult(intent, REQ_VIEW_POST);
        });

        // 동기화 버튼
        btnSync.setOnClickListener(v -> {
            if (fetchThread != null && fetchThread.isAlive()) {
                fetchThread.interrupt();
            }
            recyclerView.setVisibility(View.GONE);
            textView.setText("로딩 중...");
            startFetch(site_url + "/api/posts");
        });

        // 검색 버튼 리스너: 서버 검색 API 호출
        btnSearch.setOnClickListener(v -> {
            String q = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
            if (fetchThread != null && fetchThread.isAlive()) {
                fetchThread.interrupt();
            }
            recyclerView.setVisibility(View.GONE);
            textView.setText("검색 중...");
            try {
                if (q.isEmpty()) {
                    startFetch(site_url + "/api/posts");
                } else {
                    String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.toString());
                    startFetch(site_url + "/api/posts/search/?q=" + encoded);
                }
            } catch (Exception e) {
                // 인코딩 예외 발생 시 기본 검색 호출
                startFetch(site_url + "/api/posts/search/?q=" + q);
            }
        });

        // 키보드의 검색(엔터) 버튼 처리 -> 서버 검색 API 호출
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String q = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
                if (fetchThread != null && fetchThread.isAlive()) {
                    fetchThread.interrupt();
                }
                recyclerView.setVisibility(View.GONE);
                textView.setText("검색 중...");
                try {
                    if (q.isEmpty()) {
                        startFetch(site_url + "/api/posts");
                    } else {
                        String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.toString());
                        startFetch(site_url + "/api/posts/search/?q=" + encoded);
                    }
                } catch (Exception e) {
                    startFetch(site_url + "/api/posts/search/?q=" + q);
                }

                // 키보드 숨기기
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                return true;
            }
            return false;
        });
    }

    private void updateUI() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);
        String username = prefs.getString("username", "");

        if (isLoggedIn) {
            // 로그인 상태: 사용자 정보 표시
            buttonGroupGuest.setVisibility(View.GONE);
            buttonGroupUser.setVisibility(View.VISIBLE);
            btnAddPost.setVisibility(View.VISIBLE);
            tvUsername.setText("안녕하세요, " + username + "님");
        } else {
            // 비로그인 상태: 로그인/회원가입 버튼 표시
            buttonGroupGuest.setVisibility(View.VISIBLE);
            buttonGroupUser.setVisibility(View.GONE);
            btnAddPost.setVisibility(View.GONE);
        }
    }

    private void logout() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String token = prefs.getString("token", null);
        String username = prefs.getString("username", null);
        String password = prefs.getString("password", null);

        if (token != null && !token.isEmpty() && username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            new Thread(() -> {
                try {
                    URL url = new URL("https://cwijiq.pythonanywhere.com/api/auth/logout/");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Authorization", "Token " + token);
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setDoOutput(true);

                    org.json.JSONObject jsonParam = new org.json.JSONObject();
                    jsonParam.put("username", username);
                    jsonParam.put("password", password);

                    OutputStream os = conn.getOutputStream();
                    os.write(jsonParam.toString().getBytes("UTF-8"));
                    os.close();

                    int responseCode = conn.getResponseCode();
                    conn.disconnect();
                } catch (Exception e) {
                    // 네트워크 오류 무시 (어차피 로컬 로그아웃)
                }
                runOnUiThread(() -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.clear();
                    editor.apply();
                    Toast.makeText(MainActivate.this, "로그아웃되었습니다", Toast.LENGTH_SHORT).show();
                    updateUI();
                });
            }).start();
        } else {
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            editor.apply();
            Toast.makeText(this, "로그아웃되었습니다", Toast.LENGTH_SHORT).show();
            updateUI();
        }
    }

    public void onClickDownload(View v) {
        Log.d(TAG, "onClickDownload: 버튼 눌림, 데이터 로드 시작");
        if (fetchThread != null && fetchThread.isAlive()) {
            fetchThread.interrupt();
        }
        recyclerView.setVisibility(View.GONE);
        textView.setText("로딩 중...");
        startFetch(site_url + "/api/posts"); // 사용자 제공 엔드포인트 사용
        Toast.makeText(getApplicationContext(), "Download", Toast.LENGTH_LONG).show();
    }

    public void onClickUpload(View v) {
        Intent intent = new Intent(this, NewPostActivity.class);
        startActivityForResult(intent, REQ_VIEW_POST);
    }

    // startFetch: 백그라운드 스레드에서 API 호출 및 파싱 수행
    private void startFetch(final String apiUrl) {
        // 이전에 저장된 rawJson을 초기화해서 UI에 이전 결과가 표시되는 것을 방지
        lastRawJson = null;
        fetchThread = new Thread(() -> {
            List<Post> postList = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            // 안전: 스레드 시작 시에도 lastRawJson이 비어있도록 초기화
            lastRawJson = null;

            OkHttpClient client = NetworkClient.getClient(MainActivate.this);
            Request req = new Request.Builder().url(apiUrl).get().build();

            try (Response resp = client.newCall(req).execute()) {
                int responseCode = resp.code();
                Log.d(TAG, "startFetch: responseCode=" + responseCode);

                if (responseCode == 200) {
                    String strJson = resp.body() != null ? resp.body().string() : "";
                    lastRawJson = strJson;
                    Log.d(TAG, "startFetch: raw json=" + strJson);

                    JSONArray aryJson = null;
                    try {
                        aryJson = new JSONArray(strJson);
                    } catch (JSONException ex) {
                        // not an array; try object forms
                        try {
                            JSONObject root = new JSONObject(strJson);
                            if (root.has("results") && root.opt("results") instanceof JSONArray) {
                                aryJson = root.getJSONArray("results");
                            } else if (root.has("data") && root.opt("data") instanceof JSONArray) {
                                aryJson = root.getJSONArray("data");
                            } else if (root.has("id")) {
                                // single object -> create one Post
                                int id = root.optInt("id", -1);
                                String author = root.optString("author", "");
                                String title = root.optString("title", "");
                                String text = root.optString("text", root.optString("body", ""));
                                String published = root.optString("published_date", root.optString("published", ""));
                                String img = root.optString("image", "");
                                if (img.isEmpty()) img = root.optString("image_url", "");
                                if (img.isEmpty()) img = root.optString("photo", "");
                                String resolved = img.isEmpty() ? "" : resolveUrl(img);
                                boolean processed = root.optBoolean("processed", false);
                                postList.add(new Post(id, author, title, text, published, resolved, processed));
                            } else {
                                // try to find any array inside object
                                Iterator<String> keys = root.keys();
                                while (keys.hasNext()) {
                                    String k = keys.next();
                                    Object v = root.opt(k);
                                    if (v instanceof JSONArray) {
                                        aryJson = (JSONArray) v;
                                        break;
                                    }
                                }
                            }
                        } catch (JSONException je) {
                            Log.w(TAG, "startFetch: JSON 파싱 실패", je);
                        }
                    }

                    if (aryJson != null) {
                        for (int i = 0; i < aryJson.length(); i++) {
                            if (Thread.currentThread().isInterrupted()) return;
                            try {
                                JSONObject obj = aryJson.getJSONObject(i);
                                Post post = new Post(
                                    obj.optInt("id", -1),
                                    obj.optString("author", ""),
                                    obj.optString("title", ""),
                                    obj.optString("text", ""),
                                    obj.optString("published_date", ""),
                                    obj.optString("image", ""),
                                    obj.optBoolean("processed", false)
                                );
                                postList.add(post);
                            } catch (JSONException je) {
                                Log.w(TAG, "startFetch: 배열 요소 파싱 실패", je);
                            }
                        }
                    }

                    // fallback: try to extract an image URL from raw JSON if nothing parsed
                    if (postList.isEmpty() && lastRawJson != null) {
                        Pattern p = Pattern.compile("https?://[^\"'\\s,<>]+", Pattern.CASE_INSENSITIVE);
                        Matcher m = p.matcher(lastRawJson);
                        if (m.find()) {
                            String found = m.group();
                            if (!seen.contains(found)) {
                                Post p0 = new Post(-1, "", "", "", "", found, false);
                                postList.add(p0);
                            }
                        }
                    }

                } else {
                    Log.w(TAG, "startFetch: HTTP 응답 코드가 OK가 아님: " + responseCode);
                }
            } catch (IOException e) {
                Log.e(TAG, "startFetch: 예외 발생", e);
            }

            // UI 업데이트
            // 최신 게시글 순으로 정렬
            Collections.sort(postList, new Comparator<Post>() {
                @Override
                public int compare(Post p1, Post p2) {
                    String d1 = p1.getPublishedDate();
                    String d2 = p2.getPublishedDate();
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.KOREA);
                    try {
                        // +09:00 등 타임존이 붙은 경우 제거
                        if (d1 != null && d1.length() > 19) d1 = d1.substring(0, 19);
                        if (d2 != null && d2.length() > 19) d2 = d2.substring(0, 19);
                        long t1 = d1 != null ? sdf.parse(d1).getTime() : 0;
                        long t2 = d2 != null ? sdf.parse(d2).getTime() : 0;
                        return Long.compare(t2, t1); // 내림차순(최신순)
                    } catch (ParseException e) {
                        return 0;
                    }
                }
            });
            final List<Post> finalPosts = postList;
            runOnUiThread(() -> onPostsFetched(finalPosts));
        });

        fetchThread.start();
    }

    private String resolveUrl(String image) {
        String resolved = image;
        if (!image.startsWith("http")) {
            if (image.startsWith("/")) {
                resolved = site_url + image;
            } else {
                resolved = site_url + "/" + image;
            }
        }
        return resolved;
    }

    private void onPostsFetched(List<Post> posts) {
        Log.d(TAG, "onPostsFetched: posts size=" + (posts == null ? 0 : posts.size()));
        if (posts == null || posts.isEmpty()) {
            // 게시글이 없을 땐 리스트 숨김, 사용자에게 메시지를 표시하지 않음(빈 상태 유지)
            recyclerView.setVisibility(View.GONE);
            textView.setText("");
            // 디버그용으로 rawJson은 로그에 남김
            if (lastRawJson != null && !lastRawJson.isEmpty()) {
                Log.d(TAG, "rawJson when empty: " + (lastRawJson.length() > 1000 ? lastRawJson.substring(0, 1000) + "..." : lastRawJson));
            }
            Log.d(TAG, "onPostsFetched: 게시글 없음");
        } else {
            String html = "이미지 로드 성공! &nbsp;&nbsp;&nbsp; 총 글 개수: <b><font color='#FF424242'>" + posts.size() + "개</font></b>";
            Spanned sp = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY);
            textView.setText(sp, TextView.BufferType.SPANNABLE);
            // 게시글이 있을 땐 리스트 보이고 어댑터 적용
            recyclerView.setVisibility(View.VISIBLE);
            if (imageAdapter == null) {
                imageAdapter = new ImageAdapter(posts);
                recyclerView.setAdapter(imageAdapter);
            } else {
                imageAdapter.updateData(posts);
            }
            Log.d(TAG, "onPostsFetched: RecyclerView에 adapter 적용 완료");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VIEW_POST && resultCode == RESULT_OK) {
            Log.d(TAG, "onActivityResult: refreshing posts after detail activity result");
            if (fetchThread != null && fetchThread.isAlive()) {
                fetchThread.interrupt();
            }
            // UI 상태: 숨기고 로딩 표시
            recyclerView.setVisibility(View.GONE);
            textView.setText("로딩 중...");
            startFetch(site_url + "/api/posts");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 로그인 상태가 변경되었을 수 있으므로 UI 업데이트
        updateUI();

        // 토큰이 서버에서 무효화되었다고 표시된 경우 사용자에게 알림
        try {
            if (AuthHelper.isTokenInvalid(this)) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("인증 오류")
                        .setMessage("토큰이 만료되었거나 유효하지 않습니다. 다시 시도하려면 확인을 누르세요.")
                        .setPositiveButton("확인", (d, w) -> {
                            AuthHelper.clearTokenInvalid(this);
                        })
                        .setCancelable(false)
                        .show();
            }
        } catch (Exception e) {
            Log.w(TAG, "onResume: token invalid check failed", e);
        }
    }

    // 검색 기능은 현재 레이아웃에서 제거되어 performSearch는 유지하되 etSearch 관련 참조를 사용하지 않습니다.
    private void performSearch() {
        Toast.makeText(this, "검색 기능은 현재 비활성화되어 있습니다.", Toast.LENGTH_SHORT).show();
    }
}
