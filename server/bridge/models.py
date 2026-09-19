import secrets

from django.db import models


class Device(models.Model):
    """Ein gekoppeltes Gerät (z. B. das Android-Handy von Werner)."""

    name = models.CharField(max_length=64)
    # Einmaliger Pairing-Code (während der Kopplung gesetzt, danach geleert)
    pairing_code = models.CharField(max_length=32, blank=True, default="")
    created_at = models.DateTimeField(auto_now_add=True)
    # Bearer-Token, vergeben nach erfolgreichem Pairing
    token = models.CharField(max_length=64, blank=True, default="")
    # Wurde das Gerät vom Nutzer/Aiden zum Pairing freigegeben?
    authorized = models.BooleanField(default=False)

    def generate_token(self) -> str:
        token = f"pair_{secrets.token_urlsafe(32)}"
        self.pairing_code = ""  # Einmal-Code wird konsumiert
        self.token = token
        self.save(update_fields=["pairing_code", "token"])
        return token


class PairingSession(models.Model):
    """Einmalige Kopplung zwischen Nutzer und Gerät."""

    code = models.CharField(max_length=12, unique=True)
    device = models.OneToOneField(Device, on_delete=models.CASCADE)
    expires_at = models.DateTimeField()
    used = models.BooleanField(default=False)


class AccessGrant(models.Model):
    """Granulare Freigaben eines Geräts."""

    device = models.ForeignKey(Device, on_delete=models.CASCADE)
    grant_type = models.CharField(max_length=32)  # z. B. read_media, control
    allowed = models.BooleanField(default=True)


class CommandLog(models.Model):
    """Jede ausgeführte Aktion (Audit-Log, serverseitig)."""

    action = models.CharField(max_length=256)
    target = models.TextField(blank=True, default="")
    device = models.ForeignKey(Device, on_delete=models.SET_NULL, null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)