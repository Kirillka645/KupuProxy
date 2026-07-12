# -*- coding: utf-8 -*-
"""
KupuProxy for exteraGram
MTProto proxy finder with self-update.
"""

from __future__ import annotations

import json
import os
import re
import socket
import threading
import time
import traceback
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Any, Dict, List, Optional, Tuple
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from base_plugin import BasePlugin, MenuItemData, MenuItemType
from android_utils import run_on_ui_thread
from client_utils import get_last_fragment
from ui.alert import AlertDialogBuilder
from ui.settings import Divider, Header, Input, Switch, Text

try:
    from ui.bulletin import BulletinHelper
except Exception:  # pragma: no cover
    BulletinHelper = None  # type: ignore


# --- Plugin Metadata (AST-parsed, keep static) ---
__id__ = "kupu_proxy"
__name__ = "KupuProxy"
__description__ = (
    "Поиск и проверка MTProto-прокси (как KupuProxy Android).\n"
    "Команды: `.kupu` · `.kupu scan` · `.kupu use N` · `.kupu update`\n"
    "Источники: SoliSpirit, Yagami200, Kort, Argh94, Surfboard…\n"
    "Самообновление с GitHub Releases."
)
__author__ = "@Kirillka645"
__version__ = "1.0.2"
__icon__ = "exteraPlugins/1"
__min_version__ = "11.9.1"
# 1.4.0 / 1.4.3.3 — ок; не требуем 1.4.3.10
__sdk_version__ = ">=1.4.0"


PLUGIN_ID = "kupu_proxy"
GITHUB_REPO = "Kirillka645/KupuProxy"
UPDATE_URL = f"https://api.github.com/repos/{GITHUB_REPO}/releases/latest"
RAW_PLUGIN_MIRRORS = [
    f"https://raw.githubusercontent.com/{GITHUB_REPO}/main/exteragram/kupu_proxy.py",
    f"https://cdn.jsdelivr.net/gh/{GITHUB_REPO}@main/exteragram/kupu_proxy.py",
    f"https://raw.githack.com/{GITHUB_REPO}/main/exteragram/kupu_proxy.py",
    f"https://ghproxy.net/https://raw.githubusercontent.com/{GITHUB_REPO}/main/exteragram/kupu_proxy.py",
]

SOURCES: List[Dict[str, Any]] = [
    {
        "id": "solispirit",
        "name": "SoliSpirit",
        "urls": [
            "https://fastly.jsdelivr.net/gh/SoliSpirit/mtproto@master/all_proxies.txt",
            "https://raw.githack.com/SoliSpirit/mtproto/master/all_proxies.txt",
            "https://raw.githubusercontent.com/SoliSpirit/mtproto/master/all_proxies.txt",
        ],
    },
    {
        "id": "yagami",
        "name": "Yagami200",
        "urls": [
            "https://raw.githubusercontent.com/Yagami200/free-mtproto-proxies/main/all_proxies.txt",
            "https://raw.githubusercontent.com/Yagami200/free-mtproto-proxies/main/proxies.json",
            "https://cdn.jsdelivr.net/gh/Yagami200/free-mtproto-proxies@main/proxies.json",
            "https://cdn.jsdelivr.net/gh/Yagami200/free-mtproto-proxies@main/all_proxies.txt",
        ],
    },
    {
        "id": "kort",
        "name": "Kort All",
        "urls": [
            "https://cdn.jsdelivr.net/gh/kort0881/telegram-proxy-collector@main/proxy_all.txt",
            "https://raw.githack.com/kort0881/telegram-proxy-collector/main/proxy_all.txt",
            "https://raw.githubusercontent.com/kort0881/telegram-proxy-collector/main/proxy_all.txt",
        ],
    },
    {
        "id": "argh94",
        "name": "Argh94 Scraper",
        "urls": [
            "https://raw.githubusercontent.com/Argh94/telegram-proxy-scraper/main/proxy.txt",
            "https://cdn.jsdelivr.net/gh/Argh94/telegram-proxy-scraper@main/proxy.txt",
            "https://raw.githack.com/Argh94/telegram-proxy-scraper/main/proxy.txt",
        ],
    },
    {
        "id": "surfboard",
        "name": "Surfboard",
        "urls": [
            "https://cdn.jsdelivr.net/gh/Surfboardv2ray/TGProto@main/proxies.txt",
            "https://cdn.jsdelivr.net/gh/Surfboardv2ray/TGProto@main/proxies-tested.txt",
            "https://raw.githubusercontent.com/Surfboardv2ray/TGProto/main/proxies-tested.txt",
        ],
    },
    {
        "id": "aliila",
        "name": "ALIILAPRO",
        "urls": [
            "https://raw.githubusercontent.com/ALIILAPRO/MTProtoProxy/main/mtproto.txt",
            "https://cdn.jsdelivr.net/gh/ALIILAPRO/MTProtoProxy@main/mtproto.txt",
        ],
    },
]

LINK_RE = re.compile(
    r"(?:tg://proxy|https?://t\.me/proxy)\?[^\s<>'\"`)\]#,]+",
    re.IGNORECASE,
)
VERSION_RE = re.compile(r'^__version__\s*=\s*["\']([^"\']+)["\']', re.M)


def http_get(url: str, timeout: int = 18) -> str:
    req = Request(
        url,
        headers={
            "User-Agent": f"KupuProxy-exteraGram/{__version__}",
            "Accept": "*/*",
        },
    )
    with urlopen(req, timeout=timeout) as resp:  # noqa: S310
        data = resp.read()
    for enc in ("utf-8", "utf-8-sig", "latin-1"):
        try:
            return data.decode(enc)
        except Exception:
            continue
    return data.decode("utf-8", errors="ignore")


def parse_proxy_links(text: str) -> List[str]:
    found: List[str] = []
    seen = set()
    for m in LINK_RE.finditer(text or ""):
        raw = m.group(0).strip().rstrip("),];\"'")
        if raw.lower().startswith("https://t.me/proxy?"):
            raw = "tg://proxy?" + raw.split("?", 1)[1]
        elif raw.lower().startswith("http://t.me/proxy?"):
            raw = "tg://proxy?" + raw.split("?", 1)[1]
        key = raw
        if key not in seen:
            seen.add(key)
            found.append(raw)
    return found


def parse_proxy_url(url: str) -> Optional[Dict[str, Any]]:
    if not url or "proxy?" not in url:
        return None
    q = url.split("?", 1)[1]
    params: Dict[str, str] = {}
    for part in q.split("&"):
        if "=" not in part:
            continue
        k, v = part.split("=", 1)
        params[k] = v
    server = params.get("server")
    port = params.get("port")
    secret = params.get("secret")
    if not server or not port or not secret:
        return None
    try:
        port_i = int(port)
    except Exception:
        return None
    return {"server": server, "port": port_i, "secret": secret, "url": url}


def tcp_ping(server: str, port: int, timeout: float = 2.0) -> int:
    sock = None
    try:
        t0 = time.time()
        sock = socket.create_connection((server, port), timeout=timeout)
        ms = int((time.time() - t0) * 1000)
        return ms if ms > 0 else 1
    except Exception:
        return -1
    finally:
        try:
            if sock:
                sock.close()
        except Exception:
            pass


def version_tuple(v: str) -> Tuple[int, ...]:
    v = (v or "0").lstrip("vV")
    parts = []
    for p in v.split("."):
        try:
            parts.append(int(re.sub(r"\D", "", p) or "0"))
        except Exception:
            parts.append(0)
    return tuple(parts)


def is_newer(current: str, latest: str) -> bool:
    a, b = version_tuple(current), version_tuple(latest)
    n = max(len(a), len(b))
    a += (0,) * (n - len(a))
    b += (0,) * (n - len(b))
    return b > a


class KupuProxyPlugin(BasePlugin):
    def __init__(self):
        super().__init__()
        self._results: List[Dict[str, Any]] = []
        self._scanning = False
        self._lock = threading.Lock()
        self._drawer_id = None
        self._chat_id = None

    # region lifecycle

    def on_plugin_load(self):
        self.log(f"KupuProxy {__version__} loaded")
        try:
            self._drawer_id = self.add_menu_item(
                MenuItemData(
                    menu_type=MenuItemType.DRAWER_MENU,
                    text="KupuProxy",
                    subtext="MTProto прокси",
                    icon="msg_proxy",
                    on_click=self._on_drawer_click,
                    priority=50,
                )
            )
        except Exception as e:
            self.log(f"drawer menu failed: {e}")

        try:
            self._chat_id = self.add_menu_item(
                MenuItemData(
                    menu_type=MenuItemType.CHAT_ACTION_MENU,
                    text="KupuProxy",
                    subtext="Скан прокси",
                    icon="msg_proxy",
                    on_click=self._on_drawer_click,
                    priority=20,
                )
            )
        except Exception:
            pass

        try:
            self.add_on_send_message_hook(priority=5)
        except Exception as e:
            self.log(f"add_on_send_message_hook: {e}")

        if self.get_setting("auto_update", True):
            threading.Thread(target=self._bg_check_update, daemon=True).start()

    def on_plugin_unload(self):
        self._scanning = False
        self.log("KupuProxy unloaded")

    # endregion

    # region settings

    def create_settings(self) -> List[Any]:
        return [
            Header(text="KupuProxy"),
            Text(
                text=f"Версия плагина: {__version__}",
                subtext="Самообновление с GitHub · Kirillka645/KupuProxy",
            ),
            Switch(
                key="auto_update",
                text="Проверять обновления при запуске",
                default=True,
            ),
            Switch(
                key="notify_results",
                text="Показывать bulletin о результатах",
                default=True,
            ),
            Divider(),
            Header(text="Скан"),
            Input(
                key="max_check",
                text="Макс. прокси на проверку",
                default="120",
            ),
            Input(
                key="workers",
                text="Параллельных проверок",
                default="24",
            ),
            Input(
                key="timeout_sec",
                text="TCP timeout (сек)",
                default="2.0",
            ),
            Input(
                key="stop_when",
                text="Стоп после N рабочих (0 = все)",
                default="20",
            ),
            Divider(),
            Header(text="Команды"),
            Text(text="`.kupu` — справка"),
            Text(text="`.kupu scan` — скачать списки и проверить"),
            Text(text="`.kupu list` — показать последние рабочие"),
            Text(text="`.kupu use 1` — подключить прокси №N"),
            Text(text="`.kupu top` — топ-5 по пингу"),
            Text(text="`.kupu update` — обновить плагин"),
            Divider(),
            Text(
                text="Источники",
                subtext="SoliSpirit · Yagami200 · Kort · Argh94 · Surfboard · ALIILAPRO",
            ),
        ]

    # endregion

    # region UI helpers

    def _bulletin(self, text: str, error: bool = False):
        def go():
            try:
                if BulletinHelper is not None:
                    if error:
                        BulletinHelper.show_error(text)
                    else:
                        BulletinHelper.show_info(text)
                else:
                    self.log(text)
            except Exception as e:
                self.log(f"bulletin: {e} | {text}")

        run_on_ui_thread(go)

    def _toast(self, text: str):
        def go():
            try:
                from android.widget import Toast
                from client_utils import get_last_fragment

                frag = get_last_fragment()
                act = frag.getParentActivity() if frag else None
                if act:
                    Toast.makeText(act, text, Toast.LENGTH_SHORT).show()
            except Exception as e:
                self.log(f"toast: {e}")

        run_on_ui_thread(go)

    def _reply(self, dialog_id: int, text: str):
        """Best-effort reply in chat; falls back to bulletin."""
        try:
            from client_utils import send_message

            # send_message signature may vary across SDK versions
            try:
                send_message(dialog_id, text)
                return
            except TypeError:
                pass
            try:
                send_message(text, dialog_id)
                return
            except Exception:
                pass
        except Exception as e:
            self.log(f"send_message failed: {e}")
        self._bulletin(text[:180])

    def _on_drawer_click(self, context: Dict[str, Any]):
        self._show_main_dialog()

    def _show_main_dialog(self):
        def go():
            try:
                frag = get_last_fragment()
                act = frag.getParentActivity() if frag else None
                if not act:
                    self._bulletin("Нет activity", error=True)
                    return

                builder = AlertDialogBuilder(act)
                builder.set_title("KupuProxy")
                n = len(self._results)
                builder.set_message(
                    f"v{__version__}\n"
                    f"Рабочих в кэше: {n}\n\n"
                    f"• Скан — загрузить списки и проверить\n"
                    f"• Топ — лучшие по пингу\n"
                    f"• Обновить — скачать новую версию плагина\n\n"
                    f"Или команды: .kupu scan / .kupu use 1"
                )
                builder.set_positive_button("Скан", lambda b, w: self._start_scan(None))
                builder.set_neutral_button("Топ", lambda b, w: self._show_top_dialog())
                builder.set_negative_button(
                    "Обновить", lambda b, w: threading.Thread(
                        target=self._do_self_update, args=(True,), daemon=True
                    ).start()
                )
                builder.show()
            except Exception as e:
                self.log(traceback.format_exc())
                self._bulletin(f"UI error: {e}", error=True)

        run_on_ui_thread(go)

    def _show_top_dialog(self):
        def go():
            try:
                frag = get_last_fragment()
                act = frag.getParentActivity() if frag else None
                if not act:
                    return
                with self._lock:
                    items = list(self._results[:15])
                if not items:
                    self._bulletin("Сначала сделайте .kupu scan", error=True)
                    return

                labels = [
                    f"{i+1}. {p['server']}:{p['port']}  ·  {p['ping']} ms"
                    for i, p in enumerate(items)
                ]
                builder = AlertDialogBuilder(act)
                builder.set_title("Рабочие прокси")
                # setItems if available
                try:
                    builder.set_items(
                        labels,
                        lambda b, which: self._apply_proxy(items[which]["url"]),
                    )
                except Exception:
                    builder.set_message("\n".join(labels[:10]))
                    builder.set_positive_button(
                        "Подключить #1",
                        lambda b, w: self._apply_proxy(items[0]["url"]),
                    )
                builder.set_negative_button("Закрыть", None)
                builder.show()
            except Exception as e:
                self.log(traceback.format_exc())
                self._bulletin(str(e), error=True)

        run_on_ui_thread(go)

    # endregion

    # region commands

    def on_send_message_hook(self, account: int, params: Any):
        from base_plugin import HookResult, HookStrategy

        try:
            msg = getattr(params, "message", None)
            if not isinstance(msg, str):
                return HookResult()
            text = msg.strip()
            if not text.lower().startswith(".kupu"):
                return HookResult()

            # block sending command to chat
            dialog_id = getattr(params, "peer", None) or getattr(params, "dialog_id", None)
            try:
                # dialog id sometimes in other fields
                if dialog_id is None and hasattr(params, "peer"):
                    dialog_id = 0
            except Exception:
                dialog_id = 0

            # Prefer getting dialog from fragment
            try:
                frag = get_last_fragment()
                if frag and hasattr(frag, "getDialogId"):
                    dialog_id = int(frag.getDialogId())
            except Exception:
                pass

            threading.Thread(
                target=self._handle_command,
                args=(text, int(dialog_id or 0)),
                daemon=True,
            ).start()

            result = HookResult(strategy=HookStrategy.CANCEL)
            return result
        except Exception as e:
            self.log(f"cmd hook: {e}")
            return HookResult()

    def _handle_command(self, text: str, dialog_id: int):
        parts = text.strip().split()
        cmd = parts[1].lower() if len(parts) > 1 else "help"

        if cmd in ("help", "h", "?"):
            self._reply(
                dialog_id,
                "🔌 **KupuProxy** v"
                + __version__
                + "\n\n"
                + "`.kupu scan` — скачать + проверить\n"
                + "`.kupu list` / `.kupu top` — рабочие\n"
                + "`.kupu use N` — подключить №N\n"
                + "`.kupu update` — обновить плагин\n"
                + "`.kupu menu` — диалог\n",
            )
        elif cmd == "scan":
            self._start_scan(dialog_id)
        elif cmd in ("list", "top"):
            self._cmd_list(dialog_id)
        elif cmd == "use":
            n = int(parts[2]) if len(parts) > 2 and parts[2].isdigit() else 1
            self._cmd_use(dialog_id, n)
        elif cmd == "update":
            self._do_self_update(force=True, dialog_id=dialog_id)
        elif cmd == "menu":
            self._show_main_dialog()
        else:
            self._reply(dialog_id, f"Неизвестная команда: {cmd}\n`.kupu help`")

    def _cmd_list(self, dialog_id: int):
        with self._lock:
            items = list(self._results[:20])
        if not items:
            self._reply(dialog_id, "Пока пусто. Сделайте `.kupu scan`")
            return
        lines = [
            f"{i+1}. `{p['server']}:{p['port']}` — **{p['ping']} ms**"
            for i, p in enumerate(items)
        ]
        self._reply(
            dialog_id,
            f"✅ Рабочие ({len(items)}):\n" + "\n".join(lines) + "\n\n`.kupu use 1`",
        )

    def _cmd_use(self, dialog_id: int, n: int):
        with self._lock:
            items = list(self._results)
        if n < 1 or n > len(items):
            self._reply(dialog_id, f"Нет прокси №{n}. Всего: {len(items)}")
            return
        p = items[n - 1]
        self._apply_proxy(p["url"])
        self._reply(
            dialog_id,
            f"Подключаю #{n}: `{p['server']}:{p['port']}` ({p['ping']} ms)",
        )

    # endregion

    # region scan

    def _start_scan(self, dialog_id: Optional[int]):
        if self._scanning:
            self._bulletin("Уже сканирую…")
            return
        self._scanning = True
        if dialog_id:
            self._reply(dialog_id, "🔎 KupuProxy: загрузка списков…")
        else:
            self._bulletin("KupuProxy: сканирование…")

        def work():
            try:
                proxies = self._fetch_all()
                if not proxies:
                    self._scanning = False
                    msg = "Не удалось скачать списки (сеть / блокировка)"
                    if dialog_id:
                        self._reply(dialog_id, msg)
                    else:
                        self._bulletin(msg, error=True)
                    return

                max_check = self._int_setting("max_check", 120, 20, 500)
                workers = self._int_setting("workers", 24, 4, 48)
                timeout = self._float_setting("timeout_sec", 2.0, 0.8, 5.0)
                stop_when = self._int_setting("stop_when", 20, 0, 100)

                # shuffle lightly for diversity
                import random

                random.shuffle(proxies)
                batch = proxies[:max_check]

                if dialog_id:
                    self._reply(
                        dialog_id,
                        f"📥 Загружено **{len(proxies)}** уникальных\n"
                        f"Проверяю **{len(batch)}** (×{workers})…",
                    )

                results: List[Dict[str, Any]] = []
                checked = 0
                stop = False

                def check_one(url: str) -> Optional[Dict[str, Any]]:
                    info = parse_proxy_url(url)
                    if not info:
                        return None
                    ping = tcp_ping(info["server"], info["port"], timeout)
                    if ping < 0:
                        return None
                    return {
                        "url": info["url"] if info["url"].startswith("tg://") else url,
                        "server": info["server"],
                        "port": info["port"],
                        "secret": info["secret"],
                        "ping": ping,
                    }

                with ThreadPoolExecutor(max_workers=workers) as ex:
                    futs = {ex.submit(check_one, u): u for u in batch}
                    for fut in as_completed(futs):
                        if stop:
                            break
                        checked += 1
                        try:
                            item = fut.result()
                        except Exception:
                            item = None
                        if item:
                            results.append(item)
                            results.sort(key=lambda x: x["ping"])
                            if stop_when and len(results) >= stop_when:
                                stop = True

                with self._lock:
                    self._results = results

                if dialog_id:
                    if results:
                        top = "\n".join(
                            f"{i+1}. `{r['server']}:{r['port']}` — **{r['ping']} ms**"
                            for i, r in enumerate(results[:10])
                        )
                        self._reply(
                            dialog_id,
                            f"✅ Готово: **{len(results)}** рабочих "
                            f"(проверено {checked})\n\n{top}\n\n"
                            f"Подключить: `.kupu use 1`",
                        )
                    else:
                        self._reply(
                            dialog_id,
                            f"❌ Рабочих нет (проверено {checked}). "
                            f"Попробуйте снова / другую сеть.",
                        )
                else:
                    self._bulletin(
                        f"KupuProxy: {len(results)} рабочих"
                        if results
                        else "KupuProxy: рабочих нет",
                        error=not results,
                    )
                    if results:
                        self._show_top_dialog()
            except Exception as e:
                self.log(traceback.format_exc())
                if dialog_id:
                    self._reply(dialog_id, f"Ошибка: {e}")
                else:
                    self._bulletin(str(e), error=True)
            finally:
                self._scanning = False

        threading.Thread(target=work, daemon=True).start()

    def _fetch_all(self) -> List[str]:
        all_links: List[str] = []
        seen = set()
        for src in SOURCES:
            for url in src["urls"]:
                try:
                    body = http_get(url)
                    links = parse_proxy_links(body)
                    if not links:
                        continue
                    for L in links:
                        if L not in seen:
                            seen.add(L)
                            all_links.append(L)
                    self.log(f"{src['name']}: +{len(links)} from {url}")
                    break  # next source
                except Exception as e:
                    self.log(f"fetch fail {url}: {e}")
                    continue
        return all_links

    def _int_setting(self, key: str, default: int, lo: int, hi: int) -> int:
        try:
            v = int(str(self.get_setting(key, default)).strip())
            return max(lo, min(hi, v))
        except Exception:
            return default

    def _float_setting(self, key: str, default: float, lo: float, hi: float) -> float:
        try:
            v = float(str(self.get_setting(key, default)).replace(",", ".").strip())
            return max(lo, min(hi, v))
        except Exception:
            return default

    # endregion

    # region apply proxy

    def _apply_proxy(self, url: str):
        if not url.startswith("tg://"):
            if url.startswith("https://t.me/proxy?"):
                url = "tg://proxy?" + url.split("?", 1)[1]
            else:
                self._bulletin("Некорректная ссылка", error=True)
                return

        def go():
            try:
                from android.content import Intent
                from android.net import Uri

                frag = get_last_fragment()
                act = frag.getParentActivity() if frag else None
                if not act:
                    self._bulletin("Нет activity", error=True)
                    return

                # Native Telegram proxy dialog
                intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                act.startActivity(intent)
                self._bulletin("Открываю прокси в Telegram…")
            except Exception as e:
                self.log(traceback.format_exc())
                # fallback SharedConfig
                try:
                    self._apply_shared_config(url)
                except Exception as e2:
                    self._bulletin(f"Не удалось: {e2}", error=True)

        run_on_ui_thread(go)

    def _apply_shared_config(self, url: str):
        info = parse_proxy_url(url)
        if not info:
            raise RuntimeError("parse failed")
        from org.telegram.messenger import SharedConfig
        from org.telegram.tgnet import ConnectionsManager
        from org.telegram.messenger import NotificationCenter

        proxy = SharedConfig.ProxyInfo(
            info["server"],
            int(info["port"]),
            "",
            "",
            info["secret"],
        )
        SharedConfig.addProxy(proxy)
        SharedConfig.currentProxy = proxy
        SharedConfig.saveProxyList()
        # enable proxy
        try:
            SharedConfig.proxyEnabled = True
        except Exception:
            pass
        try:
            ConnectionsManager.setProxySettings(
                True,
                proxy.address,
                proxy.port,
                proxy.username,
                proxy.password,
                proxy.secret,
            )
        except Exception as e:
            self.log(f"setProxySettings: {e}")
        try:
            NotificationCenter.getGlobalInstance().postNotificationName(
                NotificationCenter.proxySettingsChanged
            )
        except Exception:
            pass
        self._bulletin(f"Прокси {info['server']}:{info['port']} включён")

    # endregion

    # region self-update

    def _bg_check_update(self):
        time.sleep(3)
        try:
            self._do_self_update(force=False, dialog_id=None)
        except Exception as e:
            self.log(f"auto update: {e}")

    def _do_self_update(self, force: bool = False, dialog_id: Optional[int] = None):
        try:
            remote_ver, source_url, body = self._fetch_remote_plugin()
            if not remote_ver:
                if force:
                    msg = "Не удалось проверить обновление"
                    if dialog_id:
                        self._reply(dialog_id, msg)
                    else:
                        self._bulletin(msg, error=True)
                return

            if not is_newer(__version__, remote_ver):
                if force:
                    msg = f"Уже актуальная версия v{__version__}"
                    if dialog_id:
                        self._reply(dialog_id, msg)
                    else:
                        self._bulletin(msg)
                return

            # Write over this file
            path = os.path.abspath(__file__)
            # also try .plugin sibling
            candidates = [path]
            if path.endswith(".py"):
                candidates.append(path[:-3] + ".plugin")
            if path.endswith(".plugin"):
                candidates.append(path[:-7] + ".py")

            written = None
            for p in candidates:
                try:
                    if os.path.isfile(p) or p == path:
                        with open(p, "w", encoding="utf-8") as f:
                            f.write(body)
                        written = p
                        break
                except Exception as e:
                    self.log(f"write {p}: {e}")

            if not written:
                # save to Downloads
                dl = "/storage/emulated/0/Download/kupu_proxy.plugin"
                with open(dl, "w", encoding="utf-8") as f:
                    f.write(body)
                written = dl
                msg = (
                    f"⬇ Скачано v{remote_ver} → `{dl}`\n"
                    f"Откройте файл в exteraGram → Install plugin\n"
                    f"(не удалось перезаписать текущий файл)"
                )
            else:
                msg = (
                    f"✅ KupuProxy обновлён: **v{__version__} → v{remote_ver}**\n"
                    f"Файл: `{written}`\n"
                    f"Перезапустите exteraGram или выкл/вкл плагин."
                )

            if dialog_id:
                self._reply(dialog_id, msg)
            else:
                self._bulletin(f"KupuProxy → v{remote_ver}. Перезапустите клиент.")
                self._show_update_done_dialog(remote_ver, written)
            self.log(f"updated from {source_url}")
        except Exception as e:
            self.log(traceback.format_exc())
            if force:
                if dialog_id:
                    self._reply(dialog_id, f"Ошибка обновления: {e}")
                else:
                    self._bulletin(str(e), error=True)

    def _show_update_done_dialog(self, ver: str, path: str):
        def go():
            try:
                frag = get_last_fragment()
                act = frag.getParentActivity() if frag else None
                if not act:
                    return
                b = AlertDialogBuilder(act)
                b.set_title(f"Обновлено до v{ver}")
                b.set_message(
                    f"Плагин записан:\n{path}\n\n"
                    f"Перезапустите exteraGram, чтобы загрузить новую версию."
                )
                b.set_positive_button("OK", None)
                b.show()
            except Exception as e:
                self.log(f"update dialog: {e}")

        run_on_ui_thread(go)

    def _fetch_remote_plugin(self) -> Tuple[Optional[str], Optional[str], Optional[str]]:
        # 1) Try GitHub release asset named kupu_proxy.py / .plugin
        try:
            meta = http_get(UPDATE_URL, timeout=12)
            data = json.loads(meta)
            tag = (data.get("tag_name") or "").lstrip("v")
            # plugin version is independent of app version; look into assets or body
            assets = data.get("assets") or []
            for a in assets:
                name = a.get("name") or ""
                if name in ("kupu_proxy.py", "kupu_proxy.plugin", "KupuProxy.plugin"):
                    url = a.get("browser_download_url")
                    if url:
                        body = http_get(url, timeout=30)
                        m = VERSION_RE.search(body)
                        ver = m.group(1) if m else tag or None
                        return ver, url, body
        except Exception as e:
            self.log(f"release api: {e}")

        # 2) Raw mirrors of plugin source in repo
        for url in RAW_PLUGIN_MIRRORS:
            try:
                body = http_get(url, timeout=25)
                if "KupuProxyPlugin" not in body and "__id__" not in body:
                    continue
                m = VERSION_RE.search(body)
                if not m:
                    continue
                return m.group(1), url, body
            except Exception as e:
                self.log(f"raw fail {url}: {e}")
                continue
        return None, None, None

    # endregion
