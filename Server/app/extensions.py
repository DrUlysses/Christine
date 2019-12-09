from flask_json import FlaskJSON
import database

json = FlaskJSON()


def init_database(app):
    app.db = database
