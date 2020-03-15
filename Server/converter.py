from pydub import AudioSegment
from pydub.exceptions import CouldntDecodeError
from os import remove


# Converts file from path to mp3
def convert(path_from, path_where):
    try:
        AudioSegment.from_file(path_from).export(path_where, format='mp3')
        remove(path_from)
        return True
    except CouldntDecodeError:
        return False
