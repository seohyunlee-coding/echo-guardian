from django.shortcuts import render, get_object_or_404, redirect
from django.contrib.auth.decorators import login_required
from django.contrib.auth.forms import UserCreationForm
from django.contrib.auth import login as auth_login
from django.http import JsonResponse
from .models import Post
from .forms import PostForm
from django.utils import timezone
import re
from django.views.decorators.http import require_POST

from rest_framework import viewsets
from rest_framework import generics
from .serializers import PostSerializer
from django.db.models import Q


class blogImage(viewsets.ModelViewSet):
    queryset = Post.objects.all()
    serializer_class = PostSerializer


class PostList(generics.ListAPIView):
    queryset = Post.objects.all()
    serializer_class = PostSerializer


class PostSearch(generics.ListAPIView):
    serializer_class = PostSerializer

    def get_queryset(self):
        q = self.request.query_params.get('q', '')
        qs = Post.objects.filter(published_date__lte=timezone.now())
        if q:
            qs = qs.filter(Q(title__icontains=q) | Q(text__icontains=q))
        return qs.order_by('-published_date')


class PostDetail(generics.RetrieveAPIView):
    queryset = Post.objects.all()
    serializer_class = PostSerializer


def post_list(request):
    posts = Post.objects.filter(published_date__lte=timezone.now()).order_by('published_date')

    try:
        separ_count = Post.objects.filter(title='분리수거 오류 감지').count()
        illegal_count = Post.objects.filter(title='무단 투기 감지').count()
    except Exception:
        separ_count = 0
        illegal_count = 0

    return render(request, 'blog/post_list.html', {
        'posts': posts,
        'separ_count': separ_count,
        'illegal_count': illegal_count,
    })


def js_test(request):
    return render(request, 'blog/js_test.html')

def post_detail(request, pk):
    post = get_object_or_404(Post, pk=pk)
    return render(request, 'blog/post_detail.html', {'post': post})

@login_required
def post_new(request):
    if request.method == "POST":
        form = PostForm(request.POST, request.FILES)
        if form.is_valid():
            post = form.save(commit=False)
            post.author = request.user
            post.published_date = timezone.now()
            post.save()
            return redirect('blog:post_detail', pk=post.pk)
    else:
        form = PostForm()
    return render(request, 'blog/post_edit.html', {'form': form, 'title': '새 신고 포스트 작성'})

@login_required
def post_edit(request, pk):
    post = get_object_or_404(Post, pk=pk)
    if request.user != post.author:
        return redirect('blog:post_detail', pk=post.pk)
    
    if request.method == "POST":
        form = PostForm(request.POST, request.FILES, instance=post)
        if form.is_valid():
            post = form.save(commit=False)
            post.published_date = timezone.now()
            post.save()
            return redirect('blog:post_detail', pk=post.pk)
    else:
        form = PostForm(instance=post)
    return render(request, 'blog/post_edit.html', {'form': form, 'post': post, 'title': '포스트 수정'})

@login_required
def post_delete(request, pk):
    post = get_object_or_404(Post, pk=pk)
    if request.user != post.author:
        return redirect('blog:post_detail', pk=post.pk)
    
    if request.method == "POST":
        post.delete()
        return redirect('blog:post_list')
    
    return render(request, 'blog/post_confirm_delete.html', {'post': post})


def register(request):
    if request.method == 'POST':
        form = UserCreationForm(request.POST)
        if form.is_valid():
            user = form.save()
            auth_login(request, user)
            return redirect('blog:post_list')
    else:
        form = UserCreationForm()
    return render(request, 'registration/register.html', {'form': form})


def _parse_detected_items(text):
    if not text:
        return []
    m = re.search(r'감지된\s*항목\s*:\s*(.*)', text)
    if not m:
        return []
    items_str = m.group(1)
    items_str = re.split(r'[\.\n]', items_str)[0]
    parts = [p.strip().lower() for p in items_str.split(',') if p.strip()]
    return parts


def stats_page(request, kind):
    if kind == 'separ':
        title_key = '분리수거 오류 감지'
        page_title = '분리수거 항목 통계'
    elif kind == 'illegal':
        title_key = '무단 투기 감지'
        page_title = '무단 투기 항목 통계'
    else:
        return render(request, 'blog/stats.html', {'page_title': '알 수 없는 통계', 'counts': {}})

    posts = Post.objects.filter(title=title_key)
    counts = {}
    for p in posts:
        items = _parse_detected_items(p.text)
        for it in items:
            counts[it] = counts.get(it, 0) + 1
    sorted_counts = sorted(counts.items(), key=lambda x: x[1], reverse=True)
    return render(request, 'blog/stats.html', {'page_title': page_title, 'counts': sorted_counts, 'kind': kind})

def stats_api_all(request):
    mapping = {
        'separ': '분리수거 오류 감지',
        'illegal': '무단 투기 감지'
    }
    result = {}
    for key, title_key in mapping.items():
        posts = Post.objects.filter(title=title_key)
        counts = {}
        for p in posts:
            items = _parse_detected_items(p.text)
            for it in items:
                counts[it] = counts.get(it, 0) + 1
        result[key] = dict(sorted(counts.items(), key=lambda x: x[1], reverse=True))

    return JsonResponse({'counts': result})


@login_required
@require_POST
def toggle_processed(request, pk):
    """Toggle or set processed state for a post. Only author or staff can change."""
    post = get_object_or_404(Post, pk=pk)
    if not (request.user.is_staff or request.user == post.author):
        return JsonResponse({'error': 'forbidden'}, status=403)

    val = request.POST.get('processed')
    if val is None:
        post.processed = not post.processed
    else:
        post.processed = str(val).lower() in ('1', 'true', 'yes', 'on')
    post.save()
    return JsonResponse({'pk': post.pk, 'processed': post.processed})
