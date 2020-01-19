from mutagen.id3 import ID3, APIC
from urllib.request import urlopen
from mutagen.mp3 import EasyMP3 as MP3
from storage import MUSIC_TEMP_STORAGE, MUSIC_STORAGE_PATH
from os.path import join
from os import walk, sep
import database

import file_manager
import spotify

#
# def is_link(message):
#     validate = URLValidator(message="invalid")
#     try:
#         return validate(message)
#     except ValidationError as e:
#         return e


# Become "title - artist.mp3" return list {[title], [artist]}
def divide_name(song_name):
    result = song_name.split(' - ')
    try:
        result[1] = result[1].split('.')[:-1]
    except IndexError:
        result.append('Unknown Artist')
    return result


def create_recognition_form(song_name):
    title, artist = divide_name(song_name)

    song_path = join(MUSIC_TEMP_STORAGE, song_name)

    form = {'song_path': song_path, 'song_name': song_name}

    file = MP3(song_path)
    if "title" in file and "artist" in file:
        form['title'] = file["title"][0]
        form['artist'] = file["artist"][0]
    elif title and artist:
        form['title'] = title
        form['artist'] = artist
    else:
        data = file_manager.recognize(song_path)
        form['title'] = data['title']
        form['artist'] = data['artist']

    results = spotify.get_metadata(title, artist)
    results = results['tracks']['items'][0]  # Get first result
    spotify_album = results['album']['name']  # Parse json dictionary
    spotify_artist = results['album']['artists'][0]['name']
    spotify_title = results['name']
    spotify_album_art = results['album']['images'][0]['url']

    form['spotify_title'] = spotify_title
    form['spotify_artist'] = spotify_artist

    if "APIC" in file:
        form['cover'] = file['APIC']
    else:
        form['cover'] = spotify_album_art

    if "album" in file:
        form['album'] = file["album"][0]
    else:
        form['album'] = spotify_album

    return form


def proceed_recognition_form(form):
    if form['song_path'] is None or form['title'] is None or form['artist'] is None or form['album'] is None:
        return False
    file = MP3(form["song_path"])
    file["title"] = form["title"]
    file["artist"] = form["artist"]
    file["album"] = form["album"]
    file.save()

    file = ID3(form["song_path"])
    file["APIC"] = APIC(
        encoding=3,
        mime='image/png',
        type=3,
        desc=u'cover',
        data=urlopen(form["cover"]).read()
    )
    file.save()
    database.update_info(form["title"], form["album"], form["artist"], form["song_path"])
    return True


def check_music_folder():
    print('-- Started music folder check --')
    files = 0
    for dirpath, _, filenames in walk(MUSIC_STORAGE_PATH):
        for _ in filenames:
            files += 1
    done = 0
    changed = 0
    for dirpath, _, filenames in walk(MUSIC_STORAGE_PATH):
        for file in filenames:
            path = join(dirpath, file)
            if database.get_by_path(path) is None:
                old_path = database.get_path(path.split(sep)[-1])
                if old_path is None:
                    print('Problem with: ' + str(path))
                else:
                    database.update_path(old_path, path)
                    changed += 1
            done += 1
            print('Done: ' + str(done) + ' out of ' + str(files) + '. Changed: ' + str(changed))
    print('-- Ended music folder check --')

