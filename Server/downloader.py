import youtube_dl
import requests
from bs4 import BeautifulSoup


# Download song and add it to the library
def download(title, artist):
    pass


def get_song_urls(song_input):
    """
    Gather all urls, titles for a search query
    from youtube
    """
    YOUTUBECLASS = 'spf-prefetch'

    html = requests.get("https://www.youtube.com/results",
                        params={'search_query': song_input})
    soup = BeautifulSoup(html.text, 'html.parser')

    soup_section = soup.findAll('a', {'rel': YOUTUBECLASS})

    # Use generator over list, since storage isn't important
    song_urls = ('https://www.youtube.com' + i.get('href')
                 for i in soup_section)
    song_titles = (i.get('title') for i in soup_section)

    youtube_list = list(zip(song_urls, song_titles))

    del song_urls
    del song_titles

    return youtube_list


def download_song(song_url, song_title):
    """
    Download a song using youtube url and song title
    """

    outtmpl = song_title + '.%(ext)s'
    ydl_opts = {
        'format': 'bestaudio/best',
        'outtmpl': outtmpl,
        'postprocessors': [
            {'key': 'FFmpegExtractAudio','preferredcodec': 'mp3',
             'preferredquality': '192',
            },
            {'key': 'FFmpegMetadata'},
        ],
    }

    with youtube_dl.YoutubeDL(ydl_opts) as ydl:
        info_dict = ydl.extract_info(song_url, download=True)


def download_and_add(link):
    pass
