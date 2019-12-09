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
    if 'file' not in request.files:
        return {'error': True}, 400
    file = request.files['file']
    if file.filename == '':
        return {'error': True}, 400
    if file and allowed_file(file.filename, current_app.config.get('ALLOWED_EXTENSIONS')):
        filename = file.filename
        path = os.path.join(current_app.config.get('MUSIC_TEMP_STORAGE'), filename)
        file.save(path)
        fm.add_song(filename, path)
        return {'success': True}, 200
    else:
        return {'error': True}, 400


# create json recognition form with links instead of files
@blueprint.route('/manage_tags', methods=['GET'])
@as_json
def manage_tags_get():
    if 'song_name' not in request.args:
        return {'error': True}, 400
    song_name = request.args['song_name']

    if song_name == '' or not fm.is_in_temp(song_name):
        return {'error': True}, 400
    if allowed_file(song_name, current_app.config.get('ALLOWED_EXTENSIONS')):
        result = jsonify(create_recognition_form(song_name))
        return result
    else:
        return {'error': True}, 400


# create json recognition form with links instead of files
@blueprint.route('/manage_tags', methods=['POST'])
@as_json
def manage_tags_post():
    if request.data != b'':
        form = loads(request.data.decode('utf8').replace("'", '"'))
        if form == '' or form['song_path'] == '':
            return {'error': True}, 400
        if proceed_recognition_form(form):
            if fm.move_to_storage(form['song_path']):
                return {'success': True}, 200
    return {'error': True}, 400


# create json form with list of requested items
@blueprint.route('/become_list', methods=['GET'])
@as_json
def become_list():
    if 'type' not in request.args:
        return {'error': True}, 400
    list_type = request.args['type']
    if list_type == '':
        return {'error': True}, 400
    elif list_type == "unmanaged":
        result = jsonify(fm.create_unmanaged_songs_form())
        return result
    elif list_type == "playlist":
        result = jsonify(player.playlist_get())
        return result
    elif list_type == "songs":
        result = jsonify(database.get_songs())
        return result
    else:
        return {'error': True}, 400


# become json with list of items
@blueprint.route('/send_list', methods=['POST'])
@as_json
def send_list():
    message = {}
    if request.data != b'':
        message = loads(request.data.decode('utf8').replace("'", '"'))
    if message['type'] == '' or message['content'] == '':
        return {'error': True}, 400
    elif message['type'] == "playlist":
        player.set_playlist(message['content'])
        return {'success': True}, 200
    else:
        return {'error': True}, 400


@blueprint.route('/current_song', methods=['GET'])
def send_current_song():
    return send_file(player.get_current_path())


@blueprint.route('/previous_song', methods=['GET'])
def send_previous_song():
    return send_file(player.get_previous_path())


@blueprint.route('/next_song', methods=['GET'])
def send_next_song():
    return send_file(player.get_next_path())


def allowed_file(filename, allowed_extensions):
    return '.' in filename and \
           filename.rsplit('.', 1)[1].lower() in allowed_extensions

