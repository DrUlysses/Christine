from pydub import AudioSegment
from pydub.exceptions import CouldntDecodeError


# Converts file from path to mp3
def convert(path_from, path_where):
    try:
        AudioSegment.from_file(path_from).export(path_where, format='mp3')
        return True
    except CouldntDecodeError:
        return False
