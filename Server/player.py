# The whole class is crap, but I have not found any better solution for lightweight player
from os import write, openpty, path
from subprocess import Popen  # , check_output
from storage import APP_DIR
from time import sleep


class Player:
    def __init__(self):
        self.is_playing = False
        self.master, self.slave = openpty()
        if self.playlist_get():
            with open(str(APP_DIR) + '/music/status', 'w+') as file:
                self.p = Popen(['mpg123', '--list', str(APP_DIR) + '/music/playlist'], stdin=self.master,
                               stdout=file, stderr=file)
        write(self.slave, b's')
        self.playlist_length = len(self.playlist_get())
        self.current_song_number = 0

    def restart(self):
        try:
            self.p.kill()
        except AttributeError:
            pass
        if self.playlist_get():
            with open(str(APP_DIR) + '/music/status', 'w+') as file:
                self.p = Popen(['mpg123', '--list', str(APP_DIR) + '/music/playlist'], stdin=self.master,
                               stdout=file, stderr=file)
        if not self.is_playing:
            write(self.slave, b's')
        return True

    def pause(self):
        write(self.slave, b's')
        self.is_playing = not self.is_playing
        return self.is_playing

    def next(self):
        if self.current_song_number < len(self.playlist_get()) - 1:
            current = self.get_current_path()
            write(self.slave, b'f')
            while current == self.get_current_path():
                sleep(0.1)
            self.current_song_number += 1
        return self.is_playing

    def previous(self):
        if self.current_song_number > 0:
            current = self.get_current_path()
            write(self.slave, b'd')
            while current == self.get_current_path():
                sleep(0.1)
            self.current_song_number -= 1
        return self.is_playing

    def beginning(self):
        write(self.slave, b'b')
        return True

    def forwards(self):
        write(self.slave, b'>')
        return True

    def backwards(self):
        write(self.slave, b'<')
        return True

    def update_list(self):
        if not self.playlist_get():
            return False
        t = path.getmtime(str(APP_DIR) + '/music/status')
        write(self.slave, b'l')
        while t == path.getmtime(str(APP_DIR) + '/music/status'):
            # tough spot
            sleep(0.1)
        self.playlist_length = len(self.playlist_get())
        return True

    # Most stupid and idiotic method of all time
    def get_current_time(self):
        if not self.playlist_get():
            return [0, 0]
        s = None
        while s is None:
            sleep(0.1)
            s = self.get_status_last_line()
        x = s
        while x == s and s[0] != '_' and s[0] != '>':
            self.pause()
            write(self.slave, b'v')
            self.pause()
            write(self.slave, b'v')
            sleep(0.1)
            s = self.get_status_last_line()
        res = s[13:30].split('+')
        res[0] = self.time_to_ms(res[0])
        res[1] = self.time_to_ms(res[1])
        return res

    def get_status(self):
        a = self.get_current_time()
        sleep(0.1)
        b = self.get_current_time()
        if a[0] == b[0]:
            return 'paused'
        else:
            return 'playing'

    def get_current_path(self):
        self.update_list()
        for line in reversed(list(open(str(APP_DIR) + '/music/status'))):
            if line.startswith('> ' + str(APP_DIR)):
                return path.abspath(line[2:]).strip('\n')
        return 'none'

    def get_previous_path(self):
        self.update_list()
        found = False
        for line in reversed(list(open(str(APP_DIR) + '/music/status'))):
            if line.startswith('> ' + str(APP_DIR)):
                found = True
            if found:
                return path.abspath(line[2:]).strip('\n')
        return 'none'

    def get_next_path(self):
        self.update_list()
        res = ''
        for line in reversed(list(open(str(APP_DIR) + '/music/status'))):
            if line.startswith('> ' + str(APP_DIR)):
                return path.abspath(res).strip('\n')
            res = line[2:]
        return 'none'

    def set_playlist(self, playlist):
        with open(str(APP_DIR) + '/music/playlist', 'w') as file:
            if type(playlist) == str:
                file.write("%s\n" % playlist)
            else:
                for item in playlist:
                    file.write("%s\n" % item)
        self.restart()
        return self.is_playing

    def scroll_playlist(self, pos):
        if pos < 1 or pos > len(self.playlist_get()) - 1 - self.current_song_number:
            return False
        for i in range(pos - 1):
            self.next()
        return True

    # TODO: This is dumb
    @staticmethod
    def get_status_last_line():
        for line in reversed(list(open(str(APP_DIR) + '/music/status'))):
            return line

    @staticmethod
    def time_to_ms(time_str):
        temp = time_str.split('.')
        res = int(temp[1])
        temp = temp[0].split(':')
        res += 100 * (int(temp[1]) + 60 * int(temp[0]))
        return res * 10

    @staticmethod
    def playlist_get():
        with open(str(APP_DIR) + '/music/playlist') as f:
            res = f.readlines()
            return res

    @staticmethod
    def playlist_append(song_path):
        with open(str(APP_DIR) + '/music/playlist', 'a') as file:
            file.write("%s\n" % song_path)


player = Player()
