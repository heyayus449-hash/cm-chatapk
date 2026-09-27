"""
CM-Chat (demo) - basic playable shell APK
"""
from kivy.app import App
from kivy.uix.screenmanager import ScreenManager, Screen, FadeTransition
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.label import Label
from kivy.uix.button import Button
from kivy.uix.textinput import TextInput
from kivy.uix.scrollview import ScrollView
from kivy.uix.slider import Slider
from kivy.graphics import Color, RoundedRectangle
from kivy.clock import Clock
from kivy.core.window import Window

BG = (0x14/255, 0x19/255, 0x22/255, 1)
CARD = (0x1a/255, 0x21/255, 0x2c/255, 1)
TEXT = (0xe7/255, 0xeb/255, 0xf1/255, 1)
BLUE_SOFT = (0x6f/255, 0xb8/255, 0xd9/255, 1)
RED = (0xff/255, 0x3b/255, 0x3b/255, 1)
RED_SOFT = (0xe2/255, 0x69/255, 0x6f/255, 1)
GREEN = (0x7b/255, 0xc7/255, 0x9e/255, 1)
ORANGE = (0xe0/255, 0x79/255, 0x3e/255, 1)
GREY_LIGHT = (0x8b/255, 0x94/255, 0xa3/255, 1)

Window.clearcolor = BG

CIRCLE = [
    {"name": "Nightingale", "color": BLUE_SOFT, "unread": True},
    {"name": "Quartz", "color": ORANGE, "unread": False},
    {"name": "Driftwood", "color": GREEN, "unread": False},
    {"name": "Cipher", "color": GREY_LIGHT, "unread": False},
    {"name": "Halcyon", "color": RED_SOFT, "unread": False},
]
PANIC_PIN = "4321"


class Card(BoxLayout):
    def __init__(self, radius=16, bg=CARD, **kw):
        super().__init__(**kw)
        with self.canvas.before:
            Color(*bg)
            self._rect = RoundedRectangle(radius=[radius], pos=self.pos, size=self.size)
        self.bind(pos=self._update, size=self._update)

    def _update(self, *a):
        self._rect.pos = self.pos
        self._rect.size = self.size


class RoundBtn(Button):
    def __init__(self, bg=CARD, radius=16, **kw):
        kw.setdefault("background_normal", "")
        kw.setdefault("background_down", "")
        kw.setdefault("background_color", (0, 0, 0, 0))
        kw.setdefault("color", TEXT)
        super().__init__(**kw)
        with self.canvas.before:
            Color(*bg)
            self._rect = RoundedRectangle(radius=[radius], pos=self.pos, size=self.size)
        self.bind(pos=self._update, size=self._update)

    def _update(self, *a):
        self._rect.pos = self.pos
        self._rect.size = self.size


def logo_label():
    return Label(text="[color=5fdcff][b]CM-C[/b][/color][color=ff3b3b][b]hat[/b][/color]",
                 markup=True, font_size="22sp", size_hint_y=None, height=44)


class LockScreen(Screen):
    def __init__(self, **kw):
        super().__init__(**kw)
        self.entered = ""
        root = BoxLayout(orientation="vertical", padding=30, spacing=18)
        root.add_widget(BoxLayout(size_hint_y=0.12))
        root.add_widget(logo_label())
        root.add_widget(Label(text="Face: Wanderer", color=GREY_LIGHT, size_hint_y=None, height=26))
        self.dots = Label(text=self._dots(0), font_size="26sp", color=BLUE_SOFT, size_hint_y=None, height=50)
        root.add_widget(self.dots)
        grid = GridLayout(cols=3, spacing=14, size_hint_y=0.55)
        for n in list("123456789") + ["", "0", "<"]:
            if n == "":
                grid.add_widget(BoxLayout())
                continue
            b = RoundBtn(text=n, font_size="20sp", bg=CARD)
            b.bind(on_release=self._press)
            grid.add_widget(b)
        root.add_widget(grid)
        root.add_widget(Label(text="Ghost mode ready", color=GREEN, size_hint_y=None, height=30))
        self.add_widget(root)

    def _dots(self, n):
        return " ".join(["●" if i < n else "○" for i in range(6)])

    def _press(self, btn):
        if btn.text == "<":
            self.entered = self.entered[:-1]
        elif len(self.entered) < 6:
            self.entered += btn.text
        self.dots.text = self._dots(len(self.entered))
        if len(self.entered) == 4:
            app = App.get_running_app()
            if self.entered == PANIC_PIN:
                app.trigger_wipe()
            else:
                app.unlock()
            self.entered = ""
            self.dots.text = self._dots(0)


class WipeScreen(Screen):
    def __init__(self, **kw):
        super().__init__(**kw)
        root = BoxLayout(orientation="vertical")
        with root.canvas.before:
            Color(0, 0, 0, 1)
            self._rect = RoundedRectangle(pos=root.pos, size=root.size)
        root.bind(pos=self._update, size=self._update)
        root.add_widget(BoxLayout())
        root.add_widget(Label(text="Wiping…", color=RED, font_size="20sp", size_hint_y=None, height=40))
        root.add_widget(BoxLayout())
        self.add_widget(root)

    def _update(self, *a):
        self._rect.pos = a[0].pos
        self._rect.size = a[0].size


class ContactRow(Card):
    def __init__(self, contact, on_open, **kw):
        kw.setdefault("size_hint_y", None)
        kw.setdefault("height", 66)
        kw.setdefault("padding", 14)
        kw.setdefault("spacing", 12)
        super().__init__(bg=CARD, **kw)
        avatar = Card(bg=contact["color"], size_hint_x=None, width=40, radius=20)
        self.add_widget(avatar)
        name = Label(text=contact["name"], color=TEXT, halign="left", valign="middle")
        name.bind(size=lambda w, *_: setattr(w, "text_size", w.size))
        self.add_widget(name)
        if contact["unread"]:
            self.add_widget(Card(bg=ORANGE, size_hint_x=None, width=10, radius=5))
        btn = Button(background_color=(0, 0, 0, 0), background_normal="")
        btn.bind(on_release=lambda *_: on_open(contact))
        self.add_widget(btn)


class CircleScreen(Screen):
    def __init__(self, **kw):
        super().__init__(**kw)
        root = BoxLayout(orientation="vertical", padding=16, spacing=10)
        header = BoxLayout(size_hint_y=None, height=50)
        header.add_widget(logo_label())
        knock = RoundBtn(text="+ Knock", bg=ORANGE, size_hint_x=None, width=110)
        knock.bind(on_release=self._knock)
        header.add_widget(knock)
        root.add_widget(header)
        scroller = ScrollView()
        self.list = BoxLayout(orientation="vertical", spacing=8, size_hint_y=None)
        self.list.bind(minimum_height=self.list.setter("height"))
        for c in CIRCLE:
            self.list.add_widget(ContactRow(c, self._open_chat))
        scroller.add_widget(self.list)
        root.add_widget(scroller)
        settings_btn = RoundBtn(text="Settings", bg=CARD, size_hint_y=None, height=48)
        settings_btn.bind(on_release=lambda *_: setattr(self.manager, "current", "settings"))
        root.add_widget(settings_btn)
        self.add_widget(root)

    def _knock(self, *a):
        self.manager.get_screen("chat").flash_system("Knock sent (demo) — waiting for reply")

    def _open_chat(self, contact):
        contact["unread"] = False
        chat = self.manager.get_screen("chat")
        chat.load_contact(contact)
        self.manager.current = "chat"


class Bubble(Card):
    def __init__(self, text, mine=True, offline=False, **kw):
        kw.setdefault("size_hint_y", None)
        kw.setdefault("padding", 10)
        bg = BLUE_SOFT if mine else CARD
        if offline:
            bg = (0.3, 0.08, 0.08, 1)
        super().__init__(bg=bg, **kw)
        lbl = Label(text=text, color=(0.05, 0.05, 0.08, 1) if mine and not offline else TEXT)
        lbl.bind(texture_size=lambda w, *_: setattr(self, "height", w.texture_size[1] + 20))
        lbl.bind(size=lambda w, *_: setattr(w, "text_size", (w.width, None)))
        self.add_widget(lbl)


class ChatScreen(Screen):
    def __init__(self, **kw):
        super().__init__(**kw)
        self.contact = None
        self.cerberus_minutes = 90
        self.kill_seconds_left = 0
        self._kill_event = None
        root = BoxLayout(orientation="vertical", padding=14, spacing=8)
        top = BoxLayout(size_hint_y=None, height=40)
        back = RoundBtn(text="‹ Circle", bg=CARD, size_hint_x=None, width=90)
        back.bind(on_release=lambda *_: setattr(self.manager, "current", "circle"))
        top.add_widget(back)
        self.title_lbl = Label(text="", color=TEXT, bold=True)
        top.add_widget(self.title_lbl)
        erase = RoundBtn(text="Erase", bg=CARD, size_hint_x=None, width=80)
        erase.bind(on_release=self._erase_chat)
        top.add_widget(erase)
        root.add_widget(top)
        self.status_lbl = Label(text="", color=GREEN, size_hint_y=None, height=20, font_size="12sp")
        root.add_widget(self.status_lbl)
        bar = Card(bg=CARD, size_hint_y=None, height=44, padding=10, radius=14)
        self.cerberus_btn = RoundBtn(text="Cerberus 90m", bg=CARD)
        self.cerberus_btn.color = BLUE_SOFT
        self.cerberus_btn.bind(on_release=self._toggle_cerberus)
        bar.add_widget(self.cerberus_btn)
        self.timer_btn = RoundBtn(text="Timer Off", bg=CARD)
        self.timer_btn.color = GREY_LIGHT
        self.timer_btn.bind(on_release=self._toggle_kill_timer)
        bar.add_widget(self.timer_btn)
        root.add_widget(bar)
        scroller = ScrollView()
        self.msgs = BoxLayout(orientation="vertical", spacing=6, size_hint_y=None, padding=(0, 6))
        self.msgs.bind(minimum_height=self.msgs.setter("height"))
        scroller.add_widget(self.msgs)
        root.add_widget(scroller)
        row = BoxLayout(size_hint_y=None, height=48, spacing=8)
        self.input = TextInput(hint_text="Message...", multiline=False,
                                background_color=CARD, foreground_color=TEXT,
                                cursor_color=TEXT, padding=(12, 12))
        row.add_widget(self.input)
        send = RoundBtn(text="➤", bg=BLUE_SOFT, size_hint_x=None, width=48)
        send.bind(on_release=self._send)
        row.add_widget(send)
        root.add_widget(row)
        self.add_widget(root)

    def load_contact(self, contact):
        self.contact = contact
        self.title_lbl.text = contact["name"]
        self.status_lbl.text = "● steady"
        self.msgs.clear_widgets()
        self.msgs.add_widget(Bubble("hey, you there?", mine=False))

    def flash_system(self, text):
        self.msgs.add_widget(Bubble(text, mine=False))

    def _send(self, *a):
        text = self.input.text.strip()
        if not text:
            return
        self.input.text = ""
        self.msgs.add_widget(Bubble(text, mine=True))
        Clock.schedule_once(lambda dt: self.msgs.add_widget(Bubble("Offline. Retry?", mine=True, offline=True)), 0.6)

    def _erase_chat(self, *a):
        self.msgs.clear_widgets()

    def _toggle_cerberus(self, *a):
        self.cerberus_minutes = 180 if self.cerberus_minutes == 90 else 90
        self.cerberus_btn.text = f"Cerberus {self.cerberus_minutes}m"

    def _toggle_kill_timer(self, *a):
        if self._kill_event:
            self._kill_event.cancel()
            self._kill_event = None
            self.timer_btn.text = "Timer Off"
            self.timer_btn.color = GREY_LIGHT
            return
        self.kill_seconds_left = 8
        self.timer_btn.color = RED
        self._tick_kill(0)
        self._kill_event = Clock.schedule_interval(self._tick_kill, 1)

    def _tick_kill(self, dt):
        self.timer_btn.text = f"Kill {self.kill_seconds_left}s"
        if self.kill_seconds_left <= 0:
            if self._kill_event:
                self._kill_event.cancel()
                self._kill_event = None
            App.get_running_app().trigger_wipe()
            return
        self.kill_seconds_left -= 1


class SettingsRow(Card):
    def __init__(self, label, value="", danger=False, **kw):
        kw.setdefault("size_hint_y", None)
        kw.setdefault("height", 54)
        kw.setdefault("padding", 14)
        super().__init__(bg=CARD, **kw)
        lbl = Label(text=label, color=RED_SOFT if danger else TEXT, halign="left")
        lbl.bind(size=lambda w, *_: setattr(w, "text_size", w.size))
        self.add_widget(lbl)
        if value:
            self.add_widget(Label(text=value, color=BLUE_SOFT, size_hint_x=None, width=100))


class SettingsScreen(Screen):
    def __init__(self, **kw):
        super().__init__(**kw)
        root = BoxLayout(orientation="vertical", padding=16, spacing=10)
        top = BoxLayout(size_hint_y=None, height=40)
        back = RoundBtn(text="‹ Back", bg=CARD, size_hint_x=None, width=90)
        back.bind(on_release=lambda *_: setattr(self.manager, "current", "circle"))
        top.add_widget(back)
        top.add_widget(Label(text="Settings", color=TEXT, bold=True))
        top.add_widget(BoxLayout(size_hint_x=None, width=90))
        root.add_widget(top)
        scroller = ScrollView()
        col = BoxLayout(orientation="vertical", spacing=8, size_hint_y=None, padding=(0, 6))
        col.bind(minimum_height=col.setter("height"))
        size_card = Card(bg=CARD, size_hint_y=None, height=90, padding=14, orientation="vertical")
        size_card.add_widget(Label(text="Text Size", color=TEXT, size_hint_y=None, height=22))
        self.slider = Slider(min=-6, max=6, value=0, size_hint_y=None, height=30)
        size_card.add_widget(self.slider)
        col.add_widget(size_card)
        col.add_widget(SettingsRow("Faces (Identities)"))
        col.add_widget(SettingsRow("Circle"))
        col.add_widget(SettingsRow("Cerberus · idle auto-wipe", "90 min"))
        col.add_widget(SettingsRow("Kill Timer", "not armed"))
        col.add_widget(SettingsRow("Self-Timer (per message)", "30s"))
        col.add_widget(SettingsRow("Panic PIN"))
        col.add_widget(SettingsRow("Verify App Integrity"))
        col.add_widget(SettingsRow("About / Version"))
        scroller.add_widget(col)
        root.add_widget(scroller)
        wipe_now = RoundBtn(text="Wipe Everything Now", bg=(0.3, 0.08, 0.08, 1), size_hint_y=None, height=48)
        wipe_now.color = RED
        wipe_now.bind(on_release=lambda *_: App.get_running_app().trigger_wipe())
        root.add_widget(wipe_now)
        self.add_widget(root)


class CMChatApp(App):
    def build(self):
        self.title = "CM-Chat (demo)"
        sm = ScreenManager(transition=FadeTransition(duration=0.15))
        sm.add_widget(LockScreen(name="lock"))
        sm.add_widget(CircleScreen(name="circle"))
        sm.add_widget(ChatScreen(name="chat"))
        sm.add_widget(SettingsScreen(name="settings"))
        sm.add_widget(WipeScreen(name="wipe"))
        self.sm = sm
        return sm

    def unlock(self):
        self.sm.current = "circle"

    def trigger_wipe(self):
        self.sm.current = "wipe"
        for c in CIRCLE:
            c["unread"] = False
        self.sm.get_screen("chat").msgs.clear_widgets()
        Clock.schedule_once(lambda dt: setattr(self.sm, "current", "lock"), 1.4)


if __name__ == "__main__":
    CMChatApp().run()
