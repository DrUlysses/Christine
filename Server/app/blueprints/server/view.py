from flask import Blueprint, current_app, request, jsonify, send_file
from flask_json import as_json
import os
from checker import create_recognition_form, proceed_recognition_form
from json import loads
import file_manager as fm
from player import player
import database
blueprint = Blueprint('server', __name__)


@blueprint.route('/add_song', methods=['POST'])
@as_json
def add_song():
    print('Got add song form')
    if 'file' not in request.files:
        print('Got add song form failed: No file')
        return {'error': True}, 400
    file = request.files['file']
    if file.filename == '':
        print('Got add song form failed: No filename')
        return {'error': True}, 400
    if file and allowed_file(file.filename, current_app.config.get('ALLOWED_EXTENSIONS')):
        filename = file.filename
        path = os.path.join(current_app.config.get('MUSIC_TEMP_STORAGE'), filename)
        file.save(path)
        fm.add_song(filename, path)
        print('Added song with add song form: ' + file.filename)
        return {'success': True}, 200
    else:
        print('Got add song form failed: No filename')
        return {'error': True}, 400


# create json recognition form with links instead of files
@blueprint.route('/manage_tags', methods=['GET'])
@as_json
def manage_tags_get():
    print('Asked for manage tags form')
    if 'song_name' not in request.args:
        print('Asked for add song form failed: No song name')
        return {'error': True}, 400
    song_name = request.args['song_name']

    if song_name == '' or not fm.is_in_temp(song_name):
        print('Asked for manage tags form failed: No song name')
        return {'error': True}, 400
    if allowed_file(song_name, current_app.config.get('ALLOWED_EXTENSIONS')):
        result = jsonify(create_recognition_form(song_name))
        print('Sent manage tags form: ' + song_name)
        return result
    else:
        print('Asked for manage tags form failed: File type not allowed')
        return {'error': True}, 400


# create json recognition form with links instead of files
@blueprint.route('/manage_tags', methods=['POST'])
@as_json
def manage_tags_post():
    print('Got manage tags form')
    if request.data != b'':
        form = loads(request.data.decode('utf8').replace("'", '"'))
        if form == '' or form['song_path'] == '':
            print('Got manage tags form failed: Wrong form')
            return {'error': True}, 400
        if proceed_recognition_form(form):
            if fm.move_to_storage(form['song_path']):
                print('Managed tags from form for: ' + form['title'])
                return {'success': True}, 200
    print('Got manage tags form failed')
    return {'error': True}, 400


# create json form with list of requested items
@blueprint.route('/become_list', methods=['GET'])
@as_json
def become_list():
    print('Asked for list')
    if 'type' not in request.args:
        print('Ask for list failed: No type')
        return {'error': True}, 400
    list_type = request.args['type']
    if list_type == '':
        print('Ask for list failed: Type empty')
        return {'error': True}, 400
    elif list_type == "unmanaged":
        res = fm.create_unmanaged_songs_form()
        result = jsonify()
        print('Sent unmanaged list:')
        print(str(res))
        return result
    elif list_type == "playlist":
        res = player.playlist_get()
        result = jsonify()
        print('Sent playlist list:')
        print(str(res))
        return result
    elif list_type == "songs":
        # TODO: add possibility to request various types
        res = database.get_songs()
        if res is None:
            print('Ask for songs list failed: Songs list is empty')
            return {'error': True}, 400
        result = jsonify(res)
        print('Sent songs list:')
        print(str(res))
        return result
    else:
        print('Ask for list failed')
        return {'error': True}, 400


# become json with list of items
@blueprint.route('/send_list', methods=['POST'])
@as_json
def send_list():
    print('Got list')
    message = {}
    if request.data != b'':
        message = loads(request.data.decode('utf8').replace("'", '"'))
    if message['type'] == '' or message['content'] == '':
        print('Got list failed: Empty type or content')
        return {'error': True}, 400
    elif message['type'] == "playlist":
        playlist = []
        track_num = message['current_track_num']
        if track_num == '':
            print('Got playlist failed: Empty current track number')
            return {'error': True}, 400
        for item in message['content']:
            if database.is_added(item):
                playlist.append(item)
        player.set_playlist(playlist)
        player.scroll_playlist(track_num)
        print('Changed playlist from got list:')
        print(str(playlist))
        print('Play from song number: ' + str(track_num))
        return {'success': True}, 200
    else:
        print('Got list failed')
        return {'error': True}, 400


@blueprint.route('/current_song', methods=['GET'])
def send_current_song():
    path = player.get_current_path()
    if path is 'none':
        print('Failed to send current song: none is playing')
        return {'error': True}, 400
    print('Sent current song: ' + str(path))
    return send_file(path)


@blueprint.route('/previous_song', methods=['GET'])
def send_previous_song():
    path = player.get_previous_path()
    if path is 'none':
        print('Failed to send previous song: none is playing')
        return {'error': True}, 400
    print('Sent previous song: ' + str(path))
    return send_file(path)


@blueprint.route('/next_song', methods=['GET'])
def send_next_song():
    path = player.get_next_path()
    if path is 'none':
        print('Failed to send next song: none is playing')
        return {'error': True}, 400
    print('Sent next song: ' + str(path))
    return send_file(path)


def allowed_file(filename, allowed_extensions):
    return '.' in filename and \
           filename.rsplit('.', 1)[1].lower() in allowed_extensions

