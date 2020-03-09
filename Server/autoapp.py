from flask_server import create_app

app = create_app()
app.socketio.run(app, host='0.0.0.0')
