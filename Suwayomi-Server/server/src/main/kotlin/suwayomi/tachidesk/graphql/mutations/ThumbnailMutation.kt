package suwayomi.tachidesk.graphql.mutations

import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.graphql.server.primitives.Mutation
import suwayomi.tachidesk.manga.impl.ThumbnailDownloadHelper
import java.io.InputStream

class ThumbnailMutation : Mutation {
    override val name = "ThumbnailMutation"

    private val di by lazy { DI.global }
    private val thumbnailHelper by di.instance<ThumbnailDownloadHelper>()

    inner class SetCustomThumbnailArgs(val mangaId: Int, val thumbnail: InputStream)
    fun setCustomThumbnail(args: SetCustomThumbnailArgs):---
title: "Djangoでユーザー登録機能を実装する【django-allauth】"
date: 2020-12-17T18:12:42+09:00
draft: false
thumbnail: "images/django.jpg"
categories: [ "サーバーサイド" ]
tags: [ "django","認証","allauth","上級者向け" ]
---

django-allauthはユーザー登録、ログイン、ログアウト、パスワードリセット、ソーシャルアカウントのログインなどを簡単に実装できる。

本記事ではDjangoでdjango-allauthを使ったユーザー登録機能を作る。ソーシャルアカウントのログインなどはまた別記事で解説する。

## プロジェクトの作成とアプリのインストール

まずは適当なプロジェクトを作る。

    django-admin startproject config .

続いて、アプリを作る。アプリ名は`accounts`とする。

    python manage.py startapp accounts

settings.pyに`accounts`を追加する。

    INSTALLED_APPS = [
        'django.contrib.admin',
        'django.contrib.auth',
        'django.contrib.contenttypes',
        'django.contrib.sessions',
        'django.contrib.messages',
        'django.contrib.staticfiles',
        'accounts',
    ]


## django-allauthのインストールと設定

django-allauthをインストールする。

    pip install django-allauth

settings.pyにてdjango-allauthに必要なアプリを追加する。

    INSTALLED_APPS = [
        'django.contrib.admin',
        'django.contrib.auth',
        'django.contrib.contenttypes',
        'django.contrib.sessions',
        'django.contrib.messages',
        'django.contrib.staticfiles',
        'django.contrib.sites',
        'allauth',
        'allauth.account',
        'allauth.socialaccount',
        'accounts',
    ]

    SITE_ID = 1

    AUTHENTICATION_BACKENDS = (
        'django.contrib.auth.backends.ModelBackend',
        'allauth.account.auth_backends.AuthenticationBackend',
    )

`django.contrib.sites`はDjangoのサイトフレームワーク。django-allauthはこれを使ってサイトを管理している。`SITE_ID=1`は管理サイトでサイトオブジェクトを作った時に自動的に作られるサイトのIDが1だから。デフォルトでサイトオブジェクトが作られているので、管理サイトからサイトドメインとサイト名を変更する。

    ACCOUNT_AUTHENTICATION_METHOD = 'email'
    ACCOUNT_USERNAME_REQUIRED = True 

    ACCOUNT_EMAIL_VERIFICATION = 'mandatory'
    ACCOUNT_EMAIL_REQUIRED = True 

    LOGIN_REDIRECT_URL = 'index'
    ACCOUNT_LOGOUT_REDIRECT_URL = 'index'

    EMAIL_BACKEND = 'django.core.mail.backends.console.EmailBackend'

`ACCOUNT_AUTHENTICATION_METHOD`はログイン時にメールアドレスとパスワードで認証するか、ユーザーネームとパスワードで認証するかを指定する。`email`を指定することでメールアドレスで認証する。ユーザーネームの場合は`username`

`ACCOUNT_USERNAME_REQUIRED`はユーザーネームを入力必須にするかどうか。`True`で必須。

`ACCOUNT_EMAIL_VERIFICATION`はメールアドレスの検証をどうするか。`mandatory`は必須。`optional`は任意。

`ACCOUNT_EMAIL_REQUIRED`はメールアドレスを入力必須にするか。`True`で必須。

`LOGIN_REDIRECT_URL`はログインした後にリダイレクトするURL。`ACCOUNT_LOGOUT_REDIRECT_URL`はログアウトした後のリダイレクト先。

`EMAIL_BACKEND`はメール送信のバックエンドを指定する。`console`を指定することで、コンソール上にメールの内容を表示する。本番環境では別途設定が必要。

urls.pyにallauthのURLを指定する。

    from django.contrib import admin
    from django.urls import path,include

    urlpatterns = [
        path('admin/', admin.site.urls),
        path('accounts/', include('allauth.urls')),
    ]


続いて、マイグレーション。

    python manage.py migrate

管理サイトにログインできるスーパーユーザーを作る。

    python manage.py createsuperuser

サイトオブジェクトを変更する。管理サイトにログイン後、`Sites`の項目を開き、`example.com`を任意のドメインに変更する。実際に存在するサイトのドメインでなくてもよい。

<div class="img-center"><img src="/images/Screenshot from 2020-12-17 18-50-57.png" alt="サイトオブジェクトの変更"></div>

`example.com`を`localhost:8000`に変更した。これでdjango-allauthの設定は完了。


## トップページの作成

ログイン、ログアウト後にリダイレクトされるページを作る。まず、`templates`ディレクトリを作り、その中に`index.html`を作る。

    mkdir templates
    touch templates/index.html

settings.pyにて`TEMPLATES`の`DIRS`を編集する。

    TEMPLATES = [
        {
            'BACKEND': 'django.template.backends.django.DjangoTemplates',
            'DIRS': [ BASE_DIR / "templates" ],
            'APP_DIRS': True,
            'OPTIONS': {
                'context_processors': [
                    'django.template.context_processors.debug',
                    'django.template.context_processors.request',
                    'django.contrib.auth.context_processors.auth',
                    'django.contrib.messages.context_processors.messages',
                ],
            },
        },
    ]


index.htmlを編集する。

    <!DOCTYPE html>
    <html lang="ja">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>サンプル</title>
        <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.4.1/css/bootstrap.min.css" integrity="sha384-Vkoo8x4CGsO3+Hhxv8T/Q5PaXtkKtu6ug5TOeNV6gBiFeWPGFN9MuhOf23Q9Ifjh" crossorigin="anonymous">
    </head>
    <body>
        <main class="container">
            {% if user.is_authenticated %}
                <p>ログイン中:{{ user.username }}</p>
                <a class="btn btn-primary" href="{% url 'account_logout' %}">ログアウト</a>
            {% else %}
                <a class="btn btn-primary" href="{% url 'account_login' %}">ログイン</a>
                <a class="btn btn-primary" href="{% url 'account_signup' %}">ユーザー登録</a>
            {% endif %}
        </main>
    </body>
    </html>

`account_login`、`account_logout`、`account_signup`はdjango-allauthが提供しているURL。

ビューを作る。`accounts/views.py`を編集する。

    from django.views import generic

    class IndexView(generic.TemplateView):
        template_name = "index.html"

urls.pyにルーティングを追加する。

    from django.contrib import admin
    from django.urls import path,include

    from accounts.views import IndexView

    urlpatterns = [
        path('admin/', admin.site.urls),
        path('accounts/', include('allauth.urls')),
        path('', IndexView.as_view(),