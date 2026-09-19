from django.urls import path

from . import views

urlpatterns = [
    path("pair", views.pairing, name="pair"),
    path("pair/authorize", views.authorize_pairing, name="pair-authorize"),
    path("pair/confirm", views.confirm_pairing, name="pair-confirm"),
    path("command", views.command, name="command"),
]