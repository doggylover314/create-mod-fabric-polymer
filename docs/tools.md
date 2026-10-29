# Tools: filters, clipboard, controllers and blueprint

Create's screens are client code, so each one is a server-side chest menu (sgui) here. The behaviour behind them is
Create's.

## Attribute filter

Right-click opens the menu. Put a reference item into the ghost slot (top left); the paper/name tag
button next to it shows one of the attributes of that item. Left-click cycles forward, right-click backward
(`ItemAttribute.getAllAttributes`, the same list Create's scroll input offers). The green dye adds the shown
attribute, the red dye adds its opposite. The three coloured buttons pick how the list is used: any attribute
(allow-list), all attributes (allow-list) or none (deny-list). The barrier resets the list. The name tag shows what
is selected. Everything is written into the filter item at once.

## Package filter

The name tag shows the address; click it to change the address in an anvil text box. The barrier clears it. Wildcards
(`*`) work as on package ports.

## Clipboard

* Right-click in the air opens the editor, sneak-right-click on a block places the clipboard. A placed clipboard opens
  its editor on right-click; sneak-right-click or a punch picks it up with its content.
* Right-click a block with a clipboard to copy its settings (filters, frequencies, speeds, timings, ...), left-click
  another block to paste them. Right-click a placed clipboard to add its entries to the one in hand. Right-click a
  package port to write its address on the clipboard as a `#address` entry.
* The editor is a checklist of up to 12 entries a page and up to 50 pages. The dye in front of an entry checks it,
  the text is edited in an anvil text box (click) or removed (right-click), an empty row or the book adds an entry,
  the sponge erases the checked entries of the page, the arrows turn the page. An anvil text box takes 50
  characters, so an entry is one short line (Create's screen wraps longer text). Entries that came with an icon item
  (printed lists) show that item. Read-only clipboards can only be checked.
* Closing the editor drops blank entries and empty pages. A clipboard with nothing on it is empty again.

## Linked controller and lectern controller

A vanilla client reports the movement keys it holds, so those are the six buttons: forward (W), back (S), left (A),
right (D), jump (space) and sneak, in Create's order. While the controller is on the player is held still (movement
speed and jump strength are zero, as modifiers that are never saved).

* Right-click toggles the controller. Sneak-right-click opens the frequency menu: a column per button, two
  frequency items each.
* Right-click a redstone link with the controller to bind: press a button key and the link's frequency is stored on
  that button.
* Right-click an empty lectern to put the controller on it. Right-click the lectern to use it (right-click it again
  to let go); sneak-right-click takes the controller back.

## Crafting blueprint

Placed on a wall, floor or ceiling it grows to the largest square (up to 3x3) that fits. Right-click an empty square
or with a wrench to open the recipe editor (a 3x3 grid of ghost ingredients, which may be filters; the display slot
shows the crafted item until you put another item there). Right-click a square with a recipe to craft it from your
inventory, sneak to craft up to a stack. Hitting a square with a recipe clears it, hitting an empty one breaks the
blueprint. Players see it as display entities on the wall; the clicks are caught by thin rows of interaction
entities in front of it.
