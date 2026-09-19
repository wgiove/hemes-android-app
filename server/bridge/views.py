import secrets
from datetime import timedelta

from django.db import transaction
from django.http import JsonResponse
from django.utils import timezone
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_POST

from .models import Device, CommandLog, PairingSession


@csrf_exempt
@require_POST
def pairing(request):
    """Startet eine Kopplung: nutzer liefert Namen, Server erzeugt Einmal-Pairing-Code.

    Antwortet mit einem Pairing-Code, den die App anzeigt; der Nutzer gibt diesen
    Code (z. B. per Telegram) an Aiden weiter, um das Gerät zu aktivieren.
    """
    data = __json(request)
    name = (data.get("name") or "Unbekannt").strip()[:64]

    with transaction.atomic():
        device = Device.objects.create(name=name)
        code = secrets.token_hex(3).upper()  # 6 Zeichen, gut lesbar
        PairingSession.objects.create(
            code=code,
            device=device,
            expires_at=timezone.now() + timedelta(minutes=15),
        )

    return JsonResponse({"pairing_code": code})


@csrf_exempt
@require_POST
def authorize_pairing(request):
    """Freigibt ein Gerät nach menschlicher Bestätigung (Aiden/Telegram).

    Werner gibt den Pairing-Code an Aiden weiter; durch diesen Endpunkt wird das
    Gerät als zum Pairing freigegeben markiert. Nur dann kann die App den Token
    abholen.
    """
    data = __json(request)
    code = (data.get("code") or "").strip().upper()

    session = PairingSession.objects.filter(code=code, used=False).first()
    if session is None or session.expires_at < timezone.now():
        return JsonResponse({"error": "Ungültiger oder abgelaufener Pairing-Code."}, status=400)

    session.device.authorized = True
    session.device.save(update_fields=["authorized"])
    return JsonResponse({"authorized": True})


@csrf_exempt
@require_POST
def confirm_pairing(request):
    """App holt den Bearer-Token ab, nachdem das Gerät authorisiert wurde.

    Human-in-the-Loop ist hier die Voraussetzung: Ohne `authorize` kein Token.
    """
    data = __json(request)
    code = (data.get("code") or "").strip().upper()

    session = PairingSession.objects.filter(code=code, used=False).first()
    if session is None or session.expires_at < timezone.now():
        return JsonResponse({"error": "Ungültiger oder abgelaufener Pairing-Code."}, status=400)
    if not session.device.authorized:
        return JsonResponse({"error": "Gerät noch nicht freigegeben."}, status=403)

    session.used = True
    session.save(update_fields=["used"])
    token = session.device.generate_token()
    return JsonResponse({"token": token})


@csrf_exempt
@require_POST
def command(request):
    """Empfängt einen bestätigten Auftrag (aus der App, Human-in-the-Loop).

    Autorisierung über Bearer-Token. Schreibt immer in das Audit-Log.
    """
    device = __device_from_token(request)
    if device is None:
        return JsonResponse({"error": "Nicht autorisiert. Gerät nicht gekoppelt."}, status=401)

    data = __json(request)
    action = (data.get("action") or "").strip()[:256]
    target = (data.get("target") or "").strip()[:2000]

    if not action:
        return JsonResponse({"error": "action fehlt."}, status=400)

    CommandLog.objects.create(action=action, target=target, device=device)

    # Platzhalter: hier würde im nächsten Schritt die echte Hermes-/Qdrant-Aktion
    # ausgelöst. Für jetzt protokollieren wir und bestätigen den Empfang.
    return JsonResponse({"status": "received", "action": action, "logged": True})


def __json(request):
    try:
        return dict(request.POST) or (request.body and __safe_parse(request.body)) or {}
    except Exception:
        return {}


def __safe_parse(body: bytes) -> dict:
    import json

    try:
        return json.loads(body)
    except Exception:
        return {}


def __device_from_token(request):
    auth = request.headers.get("Authorization", "")
    token = auth.removeprefix("Bearer ").strip()
    if not token:
        return None
    return Device.objects.filter(token=token).first()