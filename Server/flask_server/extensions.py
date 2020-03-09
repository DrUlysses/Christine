from flask_json import FlaskJSON
from flask_socketio import SocketIO

import database

json = FlaskJSON()

socketio = SocketIO()


def init_database(app):
    app.db = database


def init_sockets(app):
    socketio.init_app(app)
    app.socketio = socketio

