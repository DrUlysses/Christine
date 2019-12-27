import spotify
from sqlalchemy import create_engine, Column, Integer, String
from sqlalchemy.orm import sessionmaker
from sqlalchemy.ext.declarative import declarative_base
import storage
import checker
from file_manager import rescan_temp_folder
from os.path import join

engine = create_engine("sqlite:///" + join(storage.APP_DIR, "base.db"), echo=True,
                       connect_args={'check_same_thread': False})
Session = sessionmaker(bind=engine)
session = Session()
Base = declarative_base()


# Storage db class for all the sound files
class Song(Base):
    __tablename__ = "songs"
    id = Column(Integer, primary_key=True)
    title = Column(String)
    album = Column(String)
    artist = Column(String)
    path = Column(String)
    # sec * 1000 (milliseconds)
    duration = Column(Integer)
    tags = Column(String)
    # id of the current line
    text = Column(Integer)
    # possible are: added(need to download and correct tags), saved(need to correct tags) and stored(complete)
    status = Column(String)


# Storage db class for all the texts of songs
class Lines(Base):
    __tablename__ = "lines"
    id = Column(Integer, primary_key=True)
    nextId = Column(Integer)
    firstId = Column(Integer)
    startSec = Column(Integer)
    endSec = Column(Integer)
    line = Column(String)


Base.metadata.create_all(engine)


# Add a song
def add_song(title, artist, path="", album="", duration=0, tags="", text=0, status=u'added'):
    song = Song(title=title, album=album, artist=artist, duration=duration, tags=tags, text=text,
                path=path, status=status)
    exists = session.query(Song).filter_by(title=title, artist=artist).scalar()
    # TODO: handle merge: problem - creates duplicate instead of merge
    if exists is None:
        session.add(song)
    session.commit()


# Add playlist(s) (tags) to the song
def add_tags(title, album, artist, tags):
    for song in session.query(Song).filter_by(title=title, album=album, artist=artist).all():
        for tag in tags:
            song.tags += tag + ', '
        session.merge(song)
    session.commit()


# Get songs from spotify
def add_spotify_songs():
    songs = spotify.get_tracks()
    for song in songs:
        add_song(title=song[0], album=song[1], artist=song[2], duration=song[3])


def has_song(song_name):
    song_name = checker.divide_name(song_name)
    res = session.query(Song).filter_by(title=song_name[0], artist=song_name[1]).scalar() is not None
    return res


def is_added(path):
    res = session.query(Song).filter_by(path=path).scalar() is not None
    return res


def get_path(song_name):
    res = None
    if has_song(song_name):
        song_name = checker.divide_name(song_name)
        res = session.query(Song).filter_by(title=song_name[0], artist=song_name[1]).first().path
    return res


def get_status(song_name):
    song_name = checker.divide_name(song_name)
    try:
        status = session.query(Song).filter_by(title=song_name[0], artist=song_name[1]).first().status
    except AttributeError:
        rescan_temp_folder()
        status = session.query(Song).filter_by(title=song_name[0], artist=song_name[1]).first().status
    return status


def update_info(title, album, artist, path):
    song = session.query(Song).filter_by(path=path).first()
    song.title = title
    song.artist = artist
    song.album = album
    session.commit()


def update_path(old_path, new_path, status='added'):
    song = session.query(Song).filter_by(path=old_path).first()
    song.path = new_path
    song.status = status
    session.commit()


def get_by_path(path):
    res = session.query(Song).filter_by(path=path).first()
    return res


def get_songs(status=None):
    if status is not None:
        res = session.query(Song.path, Song.title, Song.artist).filter_by(status=status)
    else:
        res = session.query(Song.path, Song.title, Song.artist)
    return res
