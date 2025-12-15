from django import forms
from .models import Post


class PostForm(forms.ModelForm):
    class Meta:
        model = Post
        fields = ('title', 'text', 'image')
        widgets = {
            'title': forms.TextInput(attrs={
                'class': 'form-control',
                'placeholder': '포스트 제목을 입력하세요',
            }),
            'text': forms.Textarea(attrs={
                'class': 'form-control',
                'placeholder': '포스트 내용을 입력하세요',
                'rows': 10,
            }),
            'image': forms.FileInput(attrs={
                'class': 'form-control',
                'accept': 'image/*',
            }),
        }
