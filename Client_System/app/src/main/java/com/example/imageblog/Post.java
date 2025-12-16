package com.example.imageblog;

public class Post {
    private int id = -1; // 새로 추가된 id 필드 (기본값 -1)
    private String author;
    private String title;
    private String text;
    private String publishedDate;
    private String imageUrl;
    private boolean done = false; // 처리 완료 상태 추가
    private boolean processed = false; // 처리 완료 상태

    // 기존 생성자(호환성 유지를 위해 그대로 둠)
    public Post(String author, String title, String text, String publishedDate, String imageUrl) {
        this.author = author;
        this.title = title;
        this.text = text;
        this.publishedDate = publishedDate;
        this.imageUrl = imageUrl;
    }

    // id를 포함한 새로운 생성자
    public Post(int id, String author, String title, String text, String publishedDate, String imageUrl, boolean processed) {
        this.id = id;
        this.author = author;
        this.title = title;
        this.text = text;
        this.publishedDate = publishedDate;
        this.imageUrl = imageUrl;
        this.processed = processed;
    }

    public int getId() {
        return id;
    }

    public String getAuthor() {
        return author;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }

    public String getPublishedDate() {
        return publishedDate;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    // 처리 완료 상태 접근자
    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public boolean getProcessed() {
        return processed;
    }
    public void setProcessed(boolean processed) {
        this.processed = processed;
    }
}
