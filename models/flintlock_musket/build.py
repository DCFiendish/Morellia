"""
Flintlock musket build script (v5 -- mcmodel.py pipeline).

Geometry is ported directly from the MODEL PARTS & DIMENSIONS table in
FlintlockMusketReferenceImage2.png (offset = `from`, offset+size = `to`,
same axis convention as the reference's COORDINATE GUIDE: +X right/length,
+Y up, +Z forward/depth).

Two deliberate deviations from the table's literal numbers, both carried
over from the earlier Blockbench-MCP build after visual comparison against
the reference's own SIDE VIEW:

  * STOCK is a single bounding box in the table (12,4,3 @ -10,-1,-1). Built
    here as three stepped boxes (comb / grip / wrist) inside that same
    envelope, because a single box reads as a flat plank -- the table only
    gives the bounding box, not the taper silhouette.
  * TRIGGER GUARD's table offset (Y: 1 to 4) places it *above* the bore,
    which cannot be a trigger guard (it has to hang below the wrist so a
    finger reaches it). The reference's own SIDE VIEW shows it hanging low;
    treated as a sign error and mirrored to Y: -4 to -1.
  * HAMMER's table Z range (-2 to -1) is exactly flush with LOCK PLATE's,
    which would coplanar-z-fight. Nudged 0.3 further out (-2.3 to -2) so it
    reads as a raised part proud of the lock plate, same fix as before.

Run:
    python build.py
"""
import sys
import os

SKILL_SCRIPTS = (
    r"C:\Users\USER\AppData\Roaming\Claude\local-agent-mode-sessions"
    r"\skills-plugin\a820f74d-5563-47ca-88ac-047e97e34659"
    r"\dcec6b20-3fab-4b93-85de-f6b4d2606639\skills\minecraft-modeling\scripts"
)
sys.path.insert(0, SKILL_SCRIPTS)
from mcmodel import Model  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))

m = Model(
    parent="item/handheld",
    textures={
        "particle": "morellia:item/flintlock_musket_wood",
        "wood": "morellia:item/flintlock_musket_wood",
        "iron": "morellia:item/flintlock_musket_iron",
        "brass": "morellia:item/flintlock_musket_brass",
        "lock_tex": "morellia:item/flintlock_musket_lock",
        "muzzle_tex": "morellia:item/flintlock_musket_muzzle",
        "buttplate_tex": "morellia:item/flintlock_musket_buttplate",
    },
)

FULL = [0, 0, 16, 16]


def solid_cube(name, frm, to, tex, uv_overrides=None):
    """cube() with every face defaulted to a full [0,0,16,16] UV instead of
    vanilla auto-UV -- our materials are flat swatches or unique crops, none
    of them tile, so position-derived auto-UV (which goes out of 0..16 for
    everything off-origin here) has nothing to offer and just adds noise to
    the validator output."""
    uv = {f: FULL for f in ("north", "south", "west", "east", "up", "down")}
    if uv_overrides:
        uv.update(uv_overrides)
    return m.cube(name, frm, to, tex=tex, uv=uv)


# -- buttplate: brass body, dedicated art on the west (butt-end) face -------
solid_cube("buttplate", (-12, -1, -1), (-11, 3, 2),
           tex={"west": "#buttplate_tex", "east": "#brass", "north": "#brass",
                "south": "#brass", "up": "#brass", "down": "#brass"})

# -- stock: stepped comb -> grip -> wrist, same envelope as the table's
#    single STOCK box (12,4,3 @ -10,-1,-1). Wrist trimmed back to x=0 (was
#    x=2 per the table) so it stops where the barrel begins instead of
#    fully overlapping it -- same Y range (0..2) as the barrel, so the
#    table's literal number put two solid, same-facing "up" surfaces on
#    the identical plane there (validator-flagged z-fight). -----------------
solid_cube("stock_comb", (-10, -1, -1), (-6, 3, 2), tex="#wood")
solid_cube("stock_grip", (-6, -1, -1), (-2, 2, 2), tex="#wood")
solid_cube("stock_wrist", (-2, 0, -0.5), (0, 2, 1.5), tex="#wood")

# -- lock plate: iron body, dedicated art on the north (outward left) face --
solid_cube("lock_plate", (-5, -2, -2), (1, 0, -1),
           tex={"north": "#lock_tex", "south": "#iron", "west": "#iron",
                "east": "#iron", "up": "#iron", "down": "#iron"})

# -- hammer: nudged proud of the lock plate face, kept on the half-pixel
#    grid (see docstring) -----------------------------------------------------
solid_cube("hammer", (-3, -2, -2.5), (-1, 0, -2), tex="#iron")

# -- trigger guard: U-shaped loop, mirrored below the wrist. The bottom
#    bar's Z range is inset by 0.01 from the legs' so the three don't share
#    an exact coplanar face at their corners (validator-flagged z-fight);
#    invisible at render scale. ------------------------------------------
solid_cube("trigger_guard_front", (-5, -4, -1), (-4, -1, 0), tex="#brass")
solid_cube("trigger_guard_back", (-3, -4, -1), (-2, -1, 0), tex="#brass")
solid_cube("trigger_guard_bottom", (-5, -4, -0.99), (-2, -3, -0.01), tex="#brass")

# -- barrel: iron tube, dedicated art on the east (muzzle) face -------------
solid_cube("barrel", (0, 0, -1), (20, 2, 1),
           tex={"east": "#muzzle_tex", "west": "#iron", "north": "#iron",
                "south": "#iron", "up": "#iron", "down": "#iron"})

# -- barrel bands (x3), brass. Sized 0.2 proud of the barrel's own 2x2
#    cross-section on Y/Z -- the table gives bands the *same* cross-section
#    as the barrel, which makes them exactly coincident with (and hidden
#    inside) the barrel rather than a visible raised ring. -----------------
for i, bx in enumerate((-2, 6, 14), start=1):
    solid_cube(f"barrel_band_{i}", (bx, -0.2, -1.2), (bx + 2, 2.2, 1.2), tex="#brass")

# -- ramrod ------------------------------------------------------------------
solid_cube("ramrod", (1, 1, 1), (19, 2, 2), tex="#wood")
solid_cube("ramrod_tip", (19, 1, 1), (21, 2, 2), tex="#brass")

m.save_java(os.path.join(HERE, "flintlock_musket.json"))
m.save_bbmodel(os.path.join(HERE, "flintlock_musket.bbmodel"))
print(f"{len(m.elements)} elements")
print("bounds:", m.bounds())
