from flask import Flask
from flask_json import as_json
from werkzeug.middleware.proxy_fix import ProxyFix
import storage
from flask_server import commands, blueprints, extensions

"""
    Creates flask application
"""


def create_app(config_object=storage):
    app = Flask(__name__.split('.')[0])
    app.config.from_object(config_object)
    register_extensions(app)
    register_blueprints(app)
    register_errorhandlers(app)
    register_commands(app)
    app.wsgi_app = ProxyFix(app.wsgi_app)
    print('Started')
    return app


def register_extensions(app):
    extensions.json.init_app(app)
    extensions.init_sockets(app)
    extensions.init_database(app)


def register_blueprints(app):
    app.register_blueprint(blueprints.blueprint, url_prefix='/')


def register_errorhandlers(app):
    @as_json
    def render_error(error):
        error_code = getattr(error, 'code', 500)
        return {}, error_code

    for errcode in [404, 500]:
        app.errorhandler(errcode)(render_error)


def register_commands(app):
    app.cli.add_command(commands.clean)
