from spotipy import Spotify
import spotipy.util as util

import storage


def get_token():
    token = util.prompt_for_user_token(storage.SPOTIFY_KEY, storage.scope,
                                       client_id=storage.SPOTIPY_CLIENT_ID,
                                       client_secret=storage.SPOTIPY_CLIENT_SECRET,
                                       redirect_uri=storage.SPOTIPY_REDIRECT_URI)
    return token


def get_tracks():
    token = get_token()
    tracks = []
    if token:
        sp = spotipy.Spotify(auth=token)
        results = sp.current_user_saved_tracks()
        all_tracks = results['items']
        while results['next']:
            results = sp.next(results)
# TODO: all the tracks must not be loaded to the one variable. This is stupid
# Rename it to add_tracks_to_database and add here each track to the database
            all_tracks.extend(results['items'])
        for item in all_tracks:
            track = item['track']
            tracks.append([track['name'], track['album']['name'], track['artists'][0]['name'], track['duration_ms']])
        else:
            print("Can't get token")
    return tracks


def get_metadata(song_name, song_artist):
    token = get_token()
    results = None
    if token:
        spotify = Spotify(auth=token)
        results = spotify.search(q=song_name + ' ' + song_artist, limit=1, type="track")
    else:
        print("Can't get token")
    return results
