import os
import cv2
import pathlib
import requests
import logging
from datetime import datetime

_LOG = logging.getLogger(__name__)

class ChangeDetection:
    result_prev = []
    HOST = 'http://127.0.0.1:8000'
    # default credentials (area1 as requested)
    username = 'area1'
    password = '123456'
    token = ''
    user_id = None
    title = ''
    text = ''
    # per-class last post timestamps (instance-level stored in __init__)
    # cooldown_seconds: hardcoded per-class repost cooldown (seconds)
    

    def get_token(self, force_refresh: bool = False) -> str:
        """Obtain token: prefer environment `BLOG_API_TOKEN`, else request remote token.
        If force_refresh True, ignore env and request anew.
        """
        # env override
        if not force_refresh:
            env = os.environ.get('BLOG_API_TOKEN')
            if env:
                return env

        token_url = 'https://cwijiq.pythonanywhere.com/api/auth/login/'
        # try up to 3 times with increased timeout
        for attempt in range(3):
            try:
                # send JSON body to new login endpoint
                res = requests.post(
                    token_url,
                    json={'username': self.username, 'password': self.password},
                    timeout=10,
                )
                if res.status_code == 200:
                    body = res.json()
                    # new API returns 'token' and 'user_id'
                    tok = body.get('token', '')
                    uid = body.get('user_id') or body.get('user') or body.get('userId')
                    if tok:
                        # store user_id if present
                        try:
                            self.user_id = int(uid) if uid is not None else None
                        except Exception:
                            self.user_id = None
                        return tok
                else:
                    _LOG.warning(f'get_token attempt {attempt+1} returned {res.status_code}: {res.text}')
            except Exception as e:
                _LOG.warning(f'get_token attempt {attempt+1} error: {e}')
        return ''

    def __init__(self, names):
        self.result_prev = [0 for i in range(len(names))]
        # cooldown configuration and per-class last-post tracking
        # hardcode 5 seconds per user request
        self.cooldown_seconds = 5
        self._last_post_times = {}  # name -> timestamp (seconds since epoch)
        # get token (env preferred, else remote)
        try:
            self.token = self.get_token()
            if not self.token:
                _LOG.warning('No token obtained in constructor')
            print(f"[ChangeDetection] auth token: {self.token!r}, user_id: {self.user_id!r}")
        except Exception as e:
            _LOG.warning(f'ChangeDetection auth init failed: {e}')
            print(f"[ChangeDetection] auth init failed: {e}")
            self.token = ''

        # track last post id and persistent list of uploaded posts
        self.last_post_id = None
        self._posted_store = pathlib.Path(os.getcwd()) / 'YOLOv5_uploaded_posts.json'
        # ensure file exists
        try:
            if not self._posted_store.exists():
                self._posted_store.write_text('[]', encoding='utf-8')
        except Exception:
            # ignore store creation errors
            pass

    def add(self, names, detected_current, save_dir, image):
        # Identify newly appeared classes (transition 0 -> 1)
        newly_added = []
        for i in range(min(len(self.result_prev), len(detected_current))):
            if self.result_prev[i] == 0 and detected_current[i] == 1:
                newly_added.append(names[i])

        # Update stored detection state
        self.result_prev = detected_current[:]  # 객체 검출 상태 저장

        # If any new object appeared, prepare title/text and send
        if newly_added:
            # apply per-class cooldown filter: only keep classes whose cooldown expired
            now_ts = datetime.now().timestamp()
            filtered_newly = []
            skipped = []
            for name in newly_added:
                last = self._last_post_times.get(name)
                if last is not None and (now_ts - last) < self.cooldown_seconds:
                    skipped.append(name)
                else:
                    filtered_newly.append(name)

            if skipped and not filtered_newly:
                # nothing new to post after cooldown filtering
                print(f"[ChangeDetection] Skipping post: classes within cooldown: {skipped}")
                return

            # proceed using filtered_newly as the newly_added set
            newly_added = filtered_newly
            # Title: classify into two categories per user rule
            # A: '분리수거 오류 감지' items
            set_a = {
                'tie','handbag','suitcase','umbrella','baseball bat','skateboard','baseballglove','tennis racket',
                'bottle','wine glass','cup','fork','knife','spoon','bowl','banana','apple','sandwich','orange',
                'broccoli','carrot','hot dog','pizza','cake'
            }
            # B: '무단 투기 감지' items
            set_b = {'chair','bicycle','dining table','tv','laptop'}

            has_a = any(name in set_a for name in newly_added)
            has_b = any(name in set_b for name in newly_added)

            if has_a and has_b:
                self.title = '분리수거 오류 및 무단 투기 감지'
            elif has_a:
                self.title = '분리수거 오류 감지'
            elif has_b:
                self.title = '무단 투기 감지'
            else:
                # fallback: comma-joined newly added class names
                self.title = ", ".join(newly_added)

            # Text: include all classes currently detected in this frame
            current_detected = [names[i] for i, v in enumerate(detected_current) if v == 1 and i < len(names)]
            # format per requirement: '감지된 항목: banana, apple, sandwich,'
            if current_detected:
                self.text = f"감지된 항목: {', '.join(current_detected)},"
            else:
                self.text = "감지된 항목:"

            # save a local copy of the detected frame for offline review
            try:
                # save to user's Desktop to make files easy to find
                local_dir = pathlib.Path.home() / 'Desktop' / 'YOLOv5_local_saved'
                local_dir.mkdir(parents=True, exist_ok=True)
                now = datetime.now()
                fname = f"{now.strftime('%Y%m%d_%H%M%S_%f')}_{self.title.replace(' ', '_')}.jpg"
                local_path = local_dir / fname
                # image is expected to be BGR (cv2) image
                try:
                    cv2.imwrite(str(local_path), image)
                except Exception:
                    # fallback: if image is numpy array in another shape, try to convert
                    try:
                        i = image.copy()
                        cv2.imwrite(str(local_path), i)
                    except Exception:
                        _LOG.debug('failed to save local detection image')
            except Exception:
                _LOG.debug('local save failed')

            # send to remote server (if configured)
            posted = self.send(save_dir, image)
            # if post succeeded, update per-class last post times for classes we just posted
            try:
                if posted:
                    now_ts2 = datetime.now().timestamp()
                    for n in newly_added:
                        self._last_post_times[n] = now_ts2
            except Exception:
                pass

    def send(self, save_dir, image):
        now = datetime.now()
        iso_now = now.isoformat()
        today = now
        save_path = pathlib.Path(os.getcwd()) / save_dir / 'detected' / str(today.year) / str(today.month) / str(today.day)
        pathlib.Path(save_path).mkdir(parents=True, exist_ok=True)
        full_path = save_path / '{0}-{1}-{2}-{3}.jpg'.format(today.hour, today.minute, today.second, today.microsecond)
        dst = cv2.resize(image, dsize=(320, 240), interpolation=cv2.INTER_AREA)
        cv2.imwrite(full_path, dst)
        # 인증이 필요한 요청에 아래의 headers를 붙임
        # Use Token auth header (DRF token auth) if token exists
        auth_header = f'Token {self.token}' if self.token else ''
        # Do NOT set Content-Type when sending files; requests will set multipart/form-data automatically
        headers = {}
        if auth_header:
            # use standard header capitalization
            headers['Authorization'] = auth_header

        # Post Create: send form-data (multipart) with author, title, text, image
        # use user_id obtained from login if available
        author_value = str(self.user_id) if getattr(self, 'user_id', None) else '1'
        data = {
            'author': author_value,
            'title': self.title,
            'text': self.text,
        }
        print(f"[ChangeDetection] POST form-data fields: {{'author': {author_value!r}, 'title': {self.title!r}}}")
        try:
            with open(full_path, 'rb') as f:
                # include filename and explicit MIME type for the image
                files = {'image': (full_path.name, f, 'image/jpeg')}
                post_url = 'https://cwijiq.pythonanywhere.com/api_root/Post/'

                # try initial POST
                res = requests.post(post_url, data=data, files=files, headers=headers, timeout=10)
                print(f"[ChangeDetection] POST {post_url} returned {res.status_code}")
                print(f"[ChangeDetection] response body: {res.text}")

                # If unauthorized, try refreshing token once and retry
                if res.status_code == 401:
                    _LOG.info('POST returned 401 - attempting token refresh and retry')
                    new_token = self.get_token(force_refresh=True)
                    if new_token:
                        self.token = new_token
                        headers['Authorization'] = f'Token {self.token}'
                        try:
                            res.close()
                        except Exception:
                            pass
                        res = requests.post(post_url, data=data, files=files, headers=headers, timeout=10)
                        print(f"[ChangeDetection] POST retry {post_url} returned {res.status_code}")
                        print(f"[ChangeDetection] retry response body: {res.text}")

                # Check final response
                try:
                    res.raise_for_status()
                except Exception:
                    _LOG.warning(f'Post returned status {getattr(res, "status_code", "?")}: {getattr(res, "text", "")})')
                    return False

                # success path: parse response and save metadata
                _LOG.info(f'Post successful: {res.status_code}')
                try:
                    body = res.json()
                except Exception:
                    body = {}

                post_id = body.get('id') or body.get('pk')
                if post_id:
                    try:
                        self.last_post_id = int(post_id)
                    except Exception:
                        self.last_post_id = None
                    # append to persistent store
                    try:
                        import json
                        arr = json.loads(self._posted_store.read_text(encoding='utf-8'))
                        arr.append({'id': self.last_post_id, 'title': self.title, 'ts': iso_now})
                        self._posted_store.write_text(json.dumps(arr, ensure_ascii=False), encoding='utf-8')
                    except Exception:
                        pass

                return True
        except Exception as e:
            _LOG.warning(f'Failed to send detection post: {e}')
            print(f"[ChangeDetection] Failed to send detection post: {e}")
            return False


