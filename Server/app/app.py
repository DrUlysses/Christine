from flask import Flask
from flask_json import as_json
from werkzeug.middleware.proxy_fix import ProxyFix
from flask_socketio import SocketIO, emit
import storage
from app import commands, blueprints, extensions
from player import player
from checker import check_music_folder

"""
    Creates flask application
"""

socketio = SocketIO()


# Can be moved to somewhere but I got circular imports
@socketio.on('connect', namespace='/status')
def on_connect():
    print('User connected to the status socket')
    emit('new_user', {'data': 'connected'})


@socketio.on('get_status', namespace='/status')
def get_status():
    print('Status requested')
    time = player.get_current_time()
    emit('status', {'status': player.get_status(), 'current_time': time[0], 'remaining_time': time[1]})


@socketio.on('check_music_folder', namespace='/status')
def rescan_music_folder():
    print('Requested to recheck music folder')
    emit('new_ones', {'new_ones': check_music_folder()})


@socketio.on('play_pause', namespace='/status')
def on_play_pause():
    status = player.pause()
    print('Now is_playing is ' + str(status))
    time = player.get_current_time()
    emit('is_playing', {'status': status, 'current_time': time[0], 'remaining_time': time[1]})


@socketio.on('play_next', namespace='/status')
def on_play_pause():
    status = player.next()
    print('Playing next. Now is_playing is ' + str(status))
    time = player.get_current_time()
    emit('is_playing', {'status': status, 'current_time': time[0], 'remaining_time': time[1]})


@socketio.on('play_previous', namespace='/status')
def on_play_pause():
    status = player.previous()
    print('Playing previous. Now is_playing is ' + str(status))
    time = player.get_current_time()
    emit('is_playing', {'status': status, 'current_time': time[0], 'remaining_time': time[1]})


@socketio.on_error_default
def error_handler(e):
    print('An error has occurred: ' + str(e))


def create_app(config_object=storage):
    app = Flask(__name__.split('.')[0])
    app.config.from_object(config_object)
    register_extensions(app)
    register_blueprints(app)
    register_errorhandlers(app)
    register_commands(app)
    app.wsgi_app = ProxyFix(app.wsgi_app)
    socketio.init_app(app)
    print('Started')
    return app


def register_extensions(app):
    extensions.json.init_app(app)


def register_blueprints(app):
    app.register_blueprint(blueprints.server.blueprint, url_prefix='/')
    extensions.init_database(app)


def register_errorhandlers(app):
    @as_json
    def render_error(error):
        error_code = getattr(error, 'code', 500)
        return {}, error_code

    for errcode in [404, 500]:
        app.errorhandler(errcode)(render_error)


def register_commands(app):
    app.cli.add_command(commands.clean)
