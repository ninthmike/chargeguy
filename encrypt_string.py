# Simple string obfuscation to discourage string dumps.
#

import sys
from Tkinter import Tk

REPLACE_DICTIONARY = "0369cf258be147ad"

def do_string_encrypt():
    r = Tk()

    if len(sys.argv) == 1:
        plaintext = r.clipboard_get()
        print('Encrypting from clipboard: %s' % plaintext)
    else:
        plaintext = sys.argv[1]

    print('Encrypting: %s' % plaintext)

    hex_text = plaintext.encode('hex')

    print('Raw hex: %s' % hex_text)

    ciphertext = ""

    for c in hex_text:
        if c >= '0' and c <= '9':
            int_value = int(c)
        else:
            int_value = ord(c) - ord('a') + 10

        ciphertext = ciphertext + REPLACE_DICTIONARY[int_value]

    print('Ciphertext: %s' % ciphertext)
    r.withdraw()
    r.clipboard_clear()
    r.clipboard_append(ciphertext)
    r.destroy()


if __name__ == '__main__':
    do_string_encrypt()
