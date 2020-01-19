import os
import checker
import converter
import chardet
from mutagen.mp3 import EasyMP3 as MP3
from mutagen.mp3 import HeaderNotFoundError
import json
import storage
import database


# Decode false encoded string
def decode_string(string):
    for coding in ['cp1252', 'cp1251']:
        try:
            temp = bytearray(string, coding)
            return temp.decode(chardet.detect(temp)['encoding'])
        except UnicodeDecodeError:
            pass
        except AttributeError:
            pass
        except UnicodeEncodeError:
            pass
    return string


def recognize(path):
    recognized = {
        'title': '',
        'artist': '',
    }
    # Load song metadata from acrcloud
    try:
        from acrcloud.recognizer import ACRCloudRecognizer
        re = ACRCloudRecognizer(storage.config)
        recognized_data = json.loads(re.recognize_by_file(path, 0))
        recognized_song = recognized_data['metadata']['music'][0]
        recognized['title'] = recognized_song['title']
        recognized['artist'] = recognized_song['artists'][0]['name']
    except KeyError:
        pass
    return recognized


def add_song(file_name, path):
    # Convert if not mp3
    if not file_name.endswith(".mp3"):
        if not converter.convert(path, os.path.join(storage.MUSIC_TEMP_STORAGE,
                                                    os.path.splitext(file_name)[0] + '.mp3')):
            return
        # TODO: check if need to remove old file
        path = os.path.join(storage.MUSIC_TEMP_STORAGE, os.path.splitext(file_name)[0] + '.mp3')
    # Read tags if necessary
    # if correct_tags:
    #    checker.correct_tags(file_path)
    try:
        file = MP3(path)
    except HeaderNotFoundError:
        return False
    # Add Song to database
    divided_name = checker.divide_name(file_name)
    database.add_song(title=divided_name[0], artist=divided_name[1], path=path,
                      duration=int(file.info.length * 1000), status=u'added')
    return True


# 'path/artist/album/Title - album.mp3'
def move_to_storage(path):
    if os.path.isfile(path) and database.is_added(path):
        song = database.get_by_path(path)
        new_path = os.path.join(storage.MUSIC_STORAGE_PATH, song.artist)
        if not os.path.exists(new_path):
            os.mkdir(new_path)
        new_path = os.path.join(new_path, song.album)
        if not os.path.exists(new_path):
            os.mkdir(new_path)
        name = os.path.split(path)[1]
        new_path = os.path.join(new_path, name)
        os.rename(path, new_path)
        database.update_path(path, new_path, 'saved')
        return True
    else:
        return False


def create_unmanaged_songs_form():
    result = {}
    length = 0
    for dirpath, _, filenames in os.walk(storage.MUSIC_TEMP_STORAGE):
        for file in filenames:
            print('Adding ' + str(file) + ' to Manage Song Form')
            result[file] = database.get_status(file)
            if length < storage.UNMANAGED_SONGS_LIST_LENGTH:
                length += 1
            else:
                break
    return result


def rescan_temp_folder():
    print('-- Started temp folder rescan --')
    files = 0
    for dirpath, _, filenames in os.walk(storage.MUSIC_TEMP_STORAGE):
        for _ in filenames:
            files += 1
    done = 0
    new_ones = 0
    for dirpath, _, filenames in os.walk(storage.MUSIC_TEMP_STORAGE):
        for file in filenames:
            done += 1
            if not database.has_song(file):
                if add_song(file, os.path.join(dirpath, file)):
                    new_ones += 1
            print('Done: ' + str(done) + ' out of ' + str(files) + '. New: ' + str(new_ones))
    print('-- Ended temp folder rescan --')
    return new_ones


def is_in_temp(song_name):
    return song_name in os.listdir(storage.MUSIC_TEMP_STORAGE) and database.has_song(song_name)
