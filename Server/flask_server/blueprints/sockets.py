from flask_socketio import emit
from player import player
from checker import check_music_folder
from flask import current_app


# Can be moved to somewhere but I got circular imports
@current_app.socketio.on('connect', namespace='/status')
def on_connect():
    print('User connected to the status socket')
    emit('new_user', {'data': 'connected'})


@current_app.socketio.on('get_status', namespace='/status')
def get_status():
    print('Status requested')
    time = player.get_current_time()
    emit('status', {'status': player.get_status(), 'current_time': time[0], 'remaining_time': time[1]})


@current_app.socketio.on('check_music_folder', namespace='/status')
def rescan_music_folder():
    print('Requested to recheck music folder')
    emit('new_ones', {'new_ones': check_music_folder()})


@current_app.socketio.on('play_pause', namespace='/status')
def on_play_pause():
    status = player.pause()
    print('Now is_playing is ' + str(status))
    time = player.get_current_time()
    emit('is_playing', {'status': status, 'current_time': time[0], 'remaining_time': time[1]})


@current_app.socketio.on('play_next', namespace='/status')
def on_play_pause():
    status = player.next()
    print('Playing next. Now is_playing is ' + str(status))
    time = player.get_current_time()
    emit('is_playing', {'status': status, 'current_time': time[0], 'remaining_time': time[1]})


@current_app.socketio.on('play_previous', namespace='/status')
def on_play_pause():
    status = player.previous()
    print('Playing previous. Now is_playing is ' + str(status))
    time = player.get_current_time()
    emit('is_playing', {'status': status, 'current_time': time[0], 'remaining_time': time[1]})


@current_app.socketio.on_error_default
def error_handler(e):
    print('An error has occurred: ' + str(e))
