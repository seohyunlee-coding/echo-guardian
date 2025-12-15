from django.urls import path, include
from . import views
from . import api_views
from rest_framework import routers

router = routers.DefaultRouter()
router.register('Post', views.blogImage)

app_name = 'blog'

urlpatterns = [
    path('', views.post_list, name='post_list'),
    path('post/<int:pk>/', views.post_detail, name='post_detail'),
    path('post/new/', views.post_new, name='post_new'),
    path('post/<int:pk>/edit/', views.post_edit, name='post_edit'),
    path('post/<int:pk>/delete/', views.post_delete, name='post_delete'),
    path('register/', views.register, name='register'),
    path('stats/separ/', views.stats_page, {'kind': 'separ'}, name='stats_separ'),
    path('stats/illegal/', views.stats_page, {'kind': 'illegal'}, name='stats_illegal'),
    path('post/<int:pk>/toggle_processed/', views.toggle_processed, name='toggle_processed'),

    path('api/stats/', views.stats_api_all, name='api_stats_all'),
    
    
    path('api/posts/', views.PostList.as_view(), name='api_post_list'),
    path('api/posts/search/', views.PostSearch.as_view(), name='api_post_search'),
    path('api/posts/<int:pk>/', views.PostDetail.as_view(), name='api_post_detail'),
    path('api/posts/recent/', api_views.api_recent_posts, name='api_post_recent'),
    path('api_root/', include(router.urls)),
    
    
    path('api/auth/login/', api_views.api_login, name='api_login'),
    path('api/auth/logout/', api_views.api_logout, name='api_logout'),
    path('api/auth/register/', api_views.api_register, name='api_register'),
    path('api/auth/user/', api_views.api_user_info, name='api_user_info'),
]
