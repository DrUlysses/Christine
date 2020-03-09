import os


class Config(object):
    SECRET_KEY = os.environ.get('APP_SECRET', '60bd7e87-29cc-43a7-825e-e30ba18462ae')

    APP_DIR = os.path.abspath(os.path.dirname(__file__))
    PROJECT_ROOT = os.path.abspath(os.path.join(APP_DIR, os.pardir))

    JSON_ADD_STATUS = True
    JSON_DATETIME_FORMAT = '%d/%m/%Y %H:%M'
    JSON_USE_ENCODE_METHODS = True


class ProdConfig(Config):
    ENV = 'production'
    DEBUG = False


class DevConfig(Config):
    ENV = 'develop'
    DEBUG = True
