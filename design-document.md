CONSTRUCTION & BLUEPRINT MOD
PROJECT DESIGN DOCUMENT

Purpose
-------
This mod provides a reusable Minecraft construction and blueprint framework.

It supports:

- Player-authored blueprints
- Blueprint-driven construction
- Construction material funding
- NPC-driven construction
- Player contributions to construction
- Persistent completed structures
- Tier upgrades
- Environmental material substitution
- Faction-sensitive visual elements
- Blueprint lineage
- Blueprint evolution chains
- Architectural style tags
- Construction-site reservations
- Front-door and future connection-point metadata

The Construction Mod is deliberately separate from Cartography, World Builder,
Government/Kyngdoms, Economy, Quests, and other gameplay systems.

Those systems may consume the Construction Mod's APIs, but the Construction Mod
does not own their domains.

======================================================================
1. CORE DESIGN PRINCIPLES
======================================================================

1. Separate capability from server rules.

   The mod provides construction capabilities. A server decides how those
   capabilities are used and what restrictions apply.

2. Separate Blueprint, Construction Project, and Completed Construction.

3. Structure Type + Tier determine construction scale and fixed economic
   requirements.

4. Blueprint determines visual architecture.

5. Construction flags reserve the maximum site immediately.

6. The current Tier occupies a centered footprint inside that reservation.

7. BP material roles preserve design intent while allowing contextual
   substitution.

8. Players and NPCs can contribute to shared construction projects.

9. Construction state must survive server restarts and crashes.

10. Player plots are separate from the Blueprint system.

11. Blueprints can have lineage and optional Tier evolution chains.

12. External systems decide how to use the Construction Mod.

======================================================================
2. CORE CONCEPTS
======================================================================

PLAYER PLOT
-----------
Persistent player-controlled land for free-form building.

A Player Plot:

- Is not controlled by the BP system.
- Cannot directly become a Blueprint.
- May increase in size as the plot's Tier increases.
- May define maximum build height.
- May define underground mineral rights.
- Uses whatever block-protection rules the server chooses.
- Is not automatically affected by political ownership changes.

CONSTRUCTION PROJECT
--------------------
A reserved site where a Blueprint-driven structure is currently being built
or upgraded.

COMPLETED CONSTRUCTION
---------------------
The persistent identity of a completed structure.

A Completed Construction survives Tier upgrades and Blueprint changes.

BLUEPRINT
---------
A reusable architectural design containing:

- Structure Type
- Tier
- Dimensions
- Actual block arrangement
- BP material roles
- Style tags
- Front Door
- Connection points
- Author
- Approval status
- Lineage
- Optional evolution-chain membership
- Version information

BLUEPRINT AUTHORING SITE
------------------------
A temporary site where a player manually creates and captures a Blueprint.

RESERVED SITE ENVELOPE
----------------------
The maximum volume reserved for a construction site.

CURRENT FOOTPRINT
-----------------
The centered footprint of the current Tier's Blueprint inside the reserved
site.

BP MATERIAL ROLE
----------------
A variable material placeholder such as:

- BP Wood Log
- BP Wood Plank
- BP Wool
- BP Banner

The role resolves into an actual Minecraft block when the Blueprint is
instantiated or when the relevant contextual state changes.

======================================================================
3. CONSTRUCTION CONTEXTS
======================================================================

                         CONSTRUCTION SYSTEM
                                  |
             +--------------------+--------------------+
             |                    |                    |
             v                    v                    v
       PLAYER PLOT       BLUEPRINT AUTHORING   BLUEPRINT CONSTRUCTION
             |                    |                    |
       Free-form             Temporary site       Max reservation
       building              Manual building      Current Tier
       Mineral rights        BP conversion       Material reservoir
       Height rules          Capture             Time requirement
       No BP capture         Approval            NPC/player funding
                                                   Completed Construction

======================================================================
4. PLAYER PLOTS
======================================================================

Player plots are intentionally outside the Blueprint system.

A player can build whatever they want within the plot's rules.

Player Plot properties may include:

- Plot Tier
- Plot area
- Maximum build height
- Maximum underground depth
- Mineral rights
- Server-defined protection rules

A plot might evolve like:

    Plot Tier 1
        |
        v
    Plot Tier 2
        |
        v
    Plot Tier 3

Each Tier can grant additional available area.

The plot can also reserve or define future expansion space so that a player's
plot can grow without creating conflicts with neighboring plots.

IMPORTANT:

A player's personal structure is not automatically converted into a Blueprint.

A player must deliberately enter Blueprint Authoring if they want to create
a reusable Blueprint.

Political affiliation changes do not automatically recolor or replace
structures on Player Plots.

======================================================================
5. CITY PLANNER
======================================================================

The City Planner is an in-world workstation used to access construction and
Blueprint functions.

Planner progression may determine:

- Maximum construction reservation
- Available structure categories
- Available Blueprint-authoring size
- Construction capabilities

Example:

    City Planner I
        -> Small structures

    City Planner II
        -> Larger structures

    City Planner III
        -> Advanced structures

    City Planner IV
        -> Major structures

    City Planner V
        -> Very large/specialized structures

Exact progression remains a server/design configuration.

======================================================================
6. CONSTRUCTION SITE PLACEMENT
======================================================================

The player does NOT need to place two corners.

The preferred interaction is:

    1. Select a construction type.
    2. Place ONE construction anchor.
    3. The anchor represents the center of the reserved site.
    4. The system determines the maximum reservation.
    5. A ghost preview appears.
    6. The player rotates/repositions the proposed site.
    7. The player reviews the site.
    8. The player confirms the placement.

Example:

                     RESERVED SITE
                10 x 10 maximum

          +---------------------+
          |                     |
          |                     |
          |     +---------+     |
          |     |         |     |
          |     | TIER 1  |     |
          |     |   4x4   |     |
          |     |         |     |
          |     +---------+     |
          |                     |
          +---------------------+
                    ^
                    |
                  CENTER

The exact Minecraft coordinate convention for even-sized sites must be
defined once during implementation so that placement is deterministic.

======================================================================
7. GHOST PREVIEW
======================================================================

The player should see a ghost representation before committing the site.

The preview should show:

- Maximum reserved footprint
- Current Tier footprint
- Actual selected Blueprint geometry
- Front Door
- Orientation
- Setbacks/clearances
- Potential future Tier footprints
- Conflicting reserved areas
- Relevant height/depth boundaries

Example:

                    STREET
                       |
                       v
                  FRONT DOOR
                       |
                +--------------+
                |              |
                |   TIER 1     |
                |   FORGE      |
                |              |
                +--------------+

        +--------------------------+
        |                          |
        |     RESERVED SITE        |
        |          10x10           |
        |                          |
        +--------------------------+

The player should be able to rotate and reposition the proposed site before
final confirmation.

======================================================================
8. BLUEPRINT AUTHORING
======================================================================

Blueprint authoring is a deliberate mode.

The player explicitly tells the system:

    "I'm building my own Blueprint."

This creates an authoring site rather than an ordinary construction project.

AUTHORING FLOW:

    Place authoring site
            |
            v
    Player builds manually
            |
            v
    Convert selected blocks into BP material roles
            |
            v
    Assign Structure Type
            |
            v
    Assign Tier
            |
            v
    Assign Style Tags
            |
            v
    Set Front Door
            |
            v
    Optional lineage / evolution chain
            |
            v
    Capture Blueprint
            |
            v
    Submit for approval
            |
            v
    Approved Blueprint Pool

Player Plots cannot simply be converted into Blueprints.

======================================================================
9. BP MATERIAL ROLES
======================================================================

The BP material system lets a Blueprint describe DESIGN INTENT rather than
requiring every block to remain permanently identical.

Example:

The player lives in a forest containing Pine.

They build their Blueprint using Pine Logs.

They decide:

    "I don't actually care that this is Pine.
     I want this to use the appropriate wood for
     whatever region the building eventually occupies."

They place Pine Logs into the BP material box and receive:

    BP Wood Log

The Blueprint now contains:

    BP_WOOD_LOG

rather than:

    PINE_LOG

Possible resolution:

    Forest   -> Pine Log
    Mountain -> Birch/Oak/etc.
    Swamp    -> Mangrove
    Other    -> Server-defined preferred wood

The same principle can apply to other materials.

Example:

    Pine Planks
        |
        v
    BP Wood Planks

    Blue Wool
        |
        v
    BP Wool / Faction Color

    Banner
        |
        v
    BP Banner / Faction Emblem

Exact blocks remain exact blocks unless the author deliberately converts
them into a BP role.

======================================================================
10. BP MATERIAL ROLE ARCHITECTURE
======================================================================

Internally, BP materials should be modeled as registered MATERIAL ROLES
rather than a permanently hard-coded collection of special blocks.

Conceptually:

    BP_WOOD_LOG
        -> Environmental Wood Resolver

    BP_WOOD_PLANK
        -> Environmental Wood Resolver

    BP_WOOL
        -> Political Color Resolver

    BP_BANNER
        -> Political Banner Resolver

Future examples could include:

    BP_ROOFING
    BP_LIGHT_SOURCE
    BP_PATH
    BP_FENCE
    BP_DECORATION

The Construction Mod provides the role mechanism.

Other systems can provide the contextual resolution.

======================================================================
11. CONTEXTUAL MATERIAL RESOLUTION
======================================================================

                         BLUEPRINT
                             |
             +---------------+---------------+
             |               |               |
         Structure       Style Tags      BP Roles
           Type
                             |
                             v
                    CONTEXT RESOLUTION
                             |
             +---------------+---------------+
             |               |               |
        Environment      Geography       Politics
             |               |               |
             +---------------+---------------+
                             |
                             v
                     ACTUAL BLOCKS

The Construction Mod should not need to understand what "Elven" means,
what a "Kingdom" is, or how a biome should choose its wood.

It exposes the data and hooks needed by systems that do understand those
concepts.

======================================================================
12. STRUCTURE TYPE VS TIER VS BLUEPRINT
======================================================================

STRUCTURE TYPE
--------------
What is being built.

Examples:

    BLACKSMITH
    BAKERY
    GUARD_POST
    WIZARD_SHOP

STRUCTURE TIER
--------------
How substantial/valuable the structure is.

Examples:

    Tier 1
    Tier 2
    Tier 3
    Tier 4
    Tier 5

BLUEPRINT
--------
What the structure actually looks like.

Examples:

    Village Forge
    Old Stone Forge
    Elven Forge
    Guild Foundry

The Blueprint does not determine the economic value.

Tier determines:

- Resource requirements
- Construction scale
- Construction time
- Current footprint
- Vertical requirements

======================================================================
13. RESERVED SITE ENVELOPE
======================================================================

The construction flags reserve the MAXIMUM POSSIBLE SITE immediately.

Example:

    Blacksmith maximum reservation = 10x10

The Tier 1 structure might only occupy 4x4.

                    MAXIMUM RESERVATION
                    10 x 10

        +---------------------------+
        |                           |
        |      +-----------+        |
        |      |           |        |
        |      |  TIER 1   |        |
        |      |    4x4    |        |
        |      |           |        |
        |      +-----------+        |
        |                           |
        +---------------------------+

The current Tier footprint is centered within the reserved site.

IMPORTANT:

The BUILDING itself does not have to be visually centered.

Only the FOOTPRINT is centered.

Example:

    +-----------+
    |XXX        |
    |XXXX       |
    |XXXXXX     |
    |XX         |
    +-----------+

This is still a centered 4x4 footprint even though the actual architecture
is intentionally concentrated toward one side.

======================================================================
14. TIER FOOTPRINT EVOLUTION
======================================================================

Example:

    Reserved Site = 10x10

    Tier 1 -> 4x4
    Tier 2 -> 5x6
    Tier 3 -> 7x7
    Tier 4 -> 8x9
    Tier 5 -> 10x10

Each Tier is centered inside the same reserved site.

The structure does NOT need to "find room" when it upgrades.

The room was reserved when the site was created.

Therefore:

    There is no possibility of:

        "Tier 2 doesn't fit."

Instead:

    Tier 2 is simply generated inside
    the already-reserved maximum site.

======================================================================
15. HEIGHT AND DEPTH
======================================================================

Structure Types can define unique vertical construction limits.

Examples:

    Bakery
        Small underground requirement
        Moderate height

    Blacksmith
        Moderate underground requirement
        Moderate height

    Guard Post
        Large underground requirement
        Moderate height

    Wizard Shop
        Moderate underground requirement
        Large height

    Watchtower
        Small underground requirement
        Very large height

This allows structures such as:

    Guard Post
        |
        +-- Underground Jail

or:

    Wizard Shop
        |
        +-- Tall tower structure

The vertical reservation is part of the construction site's maximum volume.

======================================================================
16. CONSTRUCTION PROJECT
======================================================================

Once a Blueprint-driven site is confirmed, it becomes a Construction Project.

A project contains:

    Construction ID
    Structure Type
    Target Tier
    Blueprint ID
    Reserved Site Envelope
    Current Footprint
    World Position
    Orientation
    Required Materials
    Supplied Materials
    Start Time
    Minimum Completion Time
    Default Construction Duration
    Intended Owner/Operator
    Status

======================================================================
17. CONSTRUCTION MATERIAL RESERVOIR
======================================================================

The construction box is a physical representation of the project's material
state.

It is NOT intended to function as an ordinary chest.

The project can have:

    Required:
        Stone 400
        Wood 200
        Glass 50
        Iron 25

    Supplied:
        Stone 400
        Wood 173
        Glass 20
        Iron 25

Players may contribute.

NPCs may contribute.

The project does not fundamentally care who supplied the material.

It cares about:

    TOTAL SUPPLIED
        versus
    TOTAL REQUIRED

This makes player assistance meaningful even for NPC-funded projects.

======================================================================
18. NPC CONSTRUCTION
======================================================================

A quest could cause an NPC to request a location for a business.

Example:

    NPC Baker
        |
        v
    Requests location
        |
        v
    Player grants site
        |
        v
    Bakery Construction Project
        |
        v
    NPC begins contributing materials
        |
        v
    Player contributes materials
        |
        v
    Construction progresses
        |
        v
    Bakery completed

The player can therefore help an NPC establish a business without being
the owner of the building.

======================================================================
19. PLAYER CONTRIBUTION
======================================================================

Players should be able to contribute materials to eligible construction
projects even when:

- The project is NPC-funded.
- The player is not the owner.
- The player is not the original requester.

This provides a useful social loop:

    NPC wants bakery
          |
          v
    NPC slowly gathers materials
          |
          v
    Player contributes
          |
          v
    Construction completes sooner

The server may impose permissions if desired, but the Construction Mod
should support contribution as a general capability.

======================================================================
20. CONSTRUCTION TIME
======================================================================

Construction requires BOTH:

    1. All required materials
    2. Minimum construction time elapsed

Example:

    Tier 1 Blacksmith
        Default duration = 3 hours
        Minimum duration = 1 hour

Case A:

    All materials arrive immediately.

    Completion:
        1 hour

Case B:

    99% arrives immediately.
    1 hour passes.
    Final material arrives at 1h20m.

    Completion:
        immediately at 1h20m

Case C:

    Final material arrives at 40 minutes.

    Completion:
        at 1 hour

This creates a meaningful construction window without unnecessarily forcing
a full three-hour wait after the building is completely funded.

======================================================================
21. SERVER RESTART / CRASH RECOVERY
======================================================================

The physical construction reservoir is intentionally part of the persistent
world representation.

Instead of relying on:

    "Construction progress = 73.42%"

being maintained only in memory, the server can reconstruct state from:

    Site
    + Blueprint
    + Required Materials
    + Supplied Materials
    + Start Time
    + Current Time

On server startup:

    SERVER START
         |
         v
    Find active construction sites
         |
         v
    Read project metadata
         |
         v
    Read material reservoir
         |
         v
    Calculate elapsed time
         |
         v
    Compare supplied vs required
         |
         +----> Not ready -> Resume
         |
         +----> Ready -> Complete

This makes server crashes/restarts much less likely to corrupt construction
progress.

======================================================================
22. COMPLETED CONSTRUCTION
======================================================================

A Construction Project is temporary.

When it finishes, it becomes:

    COMPLETED CONSTRUCTION

The Completed Construction is persistent.

It retains:

    Stable Construction ID
    Structure Type
    Current Tier
    Current Blueprint
    Reserved Site Envelope
    Current Footprint
    Location
    Orientation
    Front Door
    Political context
    Variable-material state
    Upgrade metadata

Other mods should interact with the Completed Construction when they need
to know what structure physically exists.

======================================================================
23. TIER UPGRADES
======================================================================

A Tier upgrade does NOT stretch the old building.

Instead:

    Completed Construction
            |
            v
    Upgrade requested
            |
            v
    Select new Tier
            |
            v
    Select appropriate Blueprint
            |
            v
    Construct new Tier inside same reservation
            |
            v
    Completed Construction updated

Example:

    Reserved Site = 10x10

    Tier 1:
        4x4
        Blueprint A

            ↓

    Tier 2:
        5x6
        Blueprint B

            ↓

    Tier 3:
        7x7
        Blueprint C

            ↓

    Tier 5:
        10x10
        Blueprint D

The Completed Construction identity remains the same.

The building may look substantially different after an upgrade.

======================================================================
24. BLUEPRINT EVOLUTION CHAINS
======================================================================

A Blueprint designer may explicitly declare:

    "These Blueprints form a chain."

Example:

    ELVEN BLACKSMITH CHAIN

    Tier 1
        Village Forge

    Tier 2
        Town Forge

    Tier 3
        Master Forge

    Tier 4
        Guild Foundry

The chain describes the intended architectural evolution of that building.

======================================================================
25. CHAIN-RESPECTING CONSUMERS
======================================================================

A Blueprint consumer can choose whether to respect an evolution chain.

CHAIN RESPECT = ON

    Tier 1
       |
       v
    Tier 2 from same chain
       |
       v
    Tier 3 from same chain

CHAIN RESPECT = OFF

    Tier 1
       |
       v
    Any valid Tier 2 Blueprint
       |
       v
    Any valid Tier 3 Blueprint

This allows different settlement cultures.

For example:

    Elven city
        -> likely chain-respecting

    Human city
        -> likely chain-respecting

    Goblin settlement
        -> may ignore chains

    Shanty settlement
        -> may select any compatible structure

These are consumer choices, not hard-coded Construction Mod rules.

======================================================================
26. BLUEPRINT LINEAGE VS EVOLUTION CHAIN
======================================================================

These are separate concepts.

LINEAGE
-------
Describes design ancestry.

Example:

    Old Stone Forge
          |
          +----> Elven Old Stone Forge
          |
          +----> Northern Forge

EVOLUTION CHAIN
---------------
Describes intentional Tier progression.

Example:

    Village Forge
          |
          v
    Town Forge
          |
          v
    Guild Forge

A Blueprint can have lineage without being part of an evolution chain.

======================================================================
27. STYLE TAGS
======================================================================

Every submitted Blueprint should use predefined style/category tags.

Examples:

    ELVEN
    HUMAN
    DWARVEN
    GOBLIN

    FOREST
    DESERT
    MOUNTAIN
    SWAMP

    ORNATE
    RUSTIC
    MILITARY
    FANTASY

The Construction Mod stores the tags.

It does not have to understand their meaning.

World Builder can use them to create coherent settlements.

Example:

    Elven City
        |
        +-- BLACKSMITH
        |     |
        |     +-- ELVEN
        |     +-- FOREST
        |
        +-- BAKERY
        |     |
        |     +-- ELVEN
        |     +-- FOREST
        |
        +-- GUARD POST
              |
              +-- ELVEN
              +-- FOREST

This prevents a town from looking like a random collection of unrelated
architectural styles.

======================================================================
28. FRONT DOOR
======================================================================

Every placeable Blueprint should be able to designate its Front Door.

The Front Door is stored relative to the Blueprint origin.

Example:

                 FRONT
                   |
                   v
             +-----------+
             |           |
             |  BUILDING |
             |           |
             +-----+-----+
                   ^
                   |
               FRONT DOOR

If the Blueprint rotates 90 degrees, the Front Door rotates with it.

The door should NOT be stored as an absolute world coordinate.

It should be:

    Relative position
    +
    Relative facing

======================================================================
29. CONNECTION POINTS
======================================================================

Internally, Front Door can be treated as a specialized CONNECTION POINT.

Future connection points could include:

    FRONT_DOOR
    BACK_DOOR
    SERVICE_ENTRANCE
    ROAD_CONNECTION
    PATH_CONNECTION
    DOCK
    MINE_ENTRANCE

Only Front Door is required initially.

The abstraction should exist so that future city-generation systems can
understand how structures connect to streets and one another.

======================================================================
30. FACTION-VARIABLE ARCHITECTURE
======================================================================

Faction-sensitive materials are represented by BP roles.

Example:

    BP Wool
        |
        v
    Current political affiliation
        |
        +----> Red Kingdom
        |
        +----> Blue Kingdom
        |
        +----> Green Kingdom

Likewise:

    BP Banner
        |
        v
    Current territory's banner/emblem

A building therefore does not permanently contain:

    "Red Wool"

It contains:

    "Faction Color"

This allows a town to change political affiliation without requiring a new
Blueprint.

======================================================================
31. POLITICAL CAPTURE
======================================================================

Example:

    Town owned by Kingdom A
            |
            v
    Town captured by Kingdom B
            |
            v
    Completed Constructions remain
            |
            v
    Political affiliation updates
            |
            v
    BP Wool / BP Banner / other political
    decorations update

The change can occur:

    Immediately

or:

    Gradually over a server-defined period.

The Construction Mod supports the mechanism.

The political system decides when the affiliation changes and how quickly
visual conversion occurs.

======================================================================
32. PLAYER STRUCTURES DURING POLITICAL CHANGE
======================================================================

Player-owned structures are intentionally separate.

If a player owns a Player Plot containing:

    Red Wool
    Red banners
    Red decorations

and the town changes faction:

    Those blocks remain unchanged.

This prevents political systems from unexpectedly modifying personal player
architecture.

Blueprint-driven political elements are different because they explicitly
declare themselves as dynamic through BP roles.

======================================================================
33. BLUEPRINT APPROVAL
======================================================================

Player-created Blueprints should have an approval lifecycle.

Possible states:

    PERSONAL
       |
       v
    PENDING REVIEW
       |
       +----> REJECTED
       |
       v
    APPROVED

Only APPROVED Blueprints enter:

    World Builder selection pools
    NPC construction pools
    Shared procedural generation pools

The author may still retain private/pending Blueprints for personal use,
depending on server rules.

======================================================================
34. SUGGESTED BLUEPRINT DATA
======================================================================

Blueprint ID
Name
Structure Type
Tier
Current Footprint
Compatible Reserved Site Size
Height Bounds
Depth Bounds
Style Tags
Exact Blocks
BP Material Roles
Front Door
Connection Points
Author
Approval State
Parent Blueprint / Lineage
Evolution Chain ID
Version
Created Timestamp
Updated Timestamp

======================================================================
35. SUGGESTED CONSTRUCTION DATA
======================================================================

Construction ID
Structure Type
Current Tier
Target Tier
Blueprint ID
Reserved Site Envelope
Current Footprint
World Position
Orientation
Required Materials
Supplied Materials
Start Time
Minimum Completion Time
Default Construction Duration
Intended Owner / Operator
Political Context Reference
Status
Upgrade Metadata

======================================================================
36. SUGGESTED API
======================================================================

BLUEPRINT

createBlueprint(...)
getBlueprint(id)
updateBlueprint(...)
submitBlueprint(id)
approveBlueprint(id)
rejectBlueprint(id, reason)
findBlueprints(criteria)
getBlueprintLineage(id)
getEvolutionChain(id)

CONSTRUCTION

createConstructionProject(...)
getConstructionProject(id)
getRequiredMaterials(id)
getSuppliedMaterials(id)
contributeMaterials(id, contribution)
getConstructionProgress(id)
upgradeConstruction(id, targetTier)
cancelConstruction(id)

PLACEMENT

previewSite(...)
validateSite(...)
createSite(...)
getReservedEnvelope(id)
getCurrentFootprint(id)

METADATA

getBlueprintTags(...)
getMaterialRoles(...)
getConnectionPoints(...)

These are conceptual API names and should be finalized during implementation.

======================================================================
37. INTEGRATION BOUNDARIES
======================================================================

WORLD BUILDER
-------------
Construction Mod provides:

    Blueprint search
    Type/Tier information
    Placement constraints
    Reserved-site information
    Current footprint
    Style tags
    Connection points
    Blueprint chains

World Builder provides:

    Town layout
    City-generation decisions
    Environment interpretation
    Selection strategy

CARTOGRAPHY
----------
Construction Mod provides:

    Structure identity
    Structure metadata
    Construction/Blueprint association hooks

Cartography provides:

    Geographic entities
    Geographic relationships
    Spatial identity

GOVERNMENT / KYNGDOMS
---------------------
Construction Mod provides:

    Faction-variable material hooks

Government provides:

    Political ownership
    Territory
    Faction identity
    Banners/emblems

ECONOMY
-------
Construction Mod provides:

    Construction requirements
    Progress
    Material contribution hooks

Economy provides:

    Prices
    Funding rules
    Economic consequences

QUESTS
------
Construction Mod provides:

    Construction project hooks

Quest system provides:

    Objectives
    Rewards
    NPC narratives

PROTECTION
----------
Construction Mod provides:

    Construction/protection hooks

Server provides:

    Who can place
    Who can break
    Who can modify
    What is protected

======================================================================
38. OVERALL ARCHITECTURE
======================================================================

                    PLAYER / NPC / WORLD BUILDER
                               |
                               v
                       CONSTRUCTION SITE
                               |
               +---------------+---------------+
               |               |               |
               v               v               v
          Reserved Site      Tier          Blueprint
           Envelope
                               |
                               v
                       MATERIAL RESERVOIR
                               |
                         Materials + Time
                               |
                               v
                   COMPLETED CONSTRUCTION
                         /           \
                        /             \
                       v               v
                  TIER UPGRADE     POLITICAL UPDATE
                       |               |
                       v               v
                New Blueprint      Resolve BP Roles
                       |               |
                       +-------+-------+
                               |
                               v
                       CURRENT STRUCTURE

======================================================================
39. EXAMPLE: ELVEN BLACKSMITH
======================================================================

SITE

    Structure Type:
        BLACKSMITH

    Maximum Reservation:
        10x10

    Vertical Bounds:
        Type-defined

TIER 1

    Blueprint:
        Elven Village Forge

    Footprint:
        4x4

    Tags:
        ELVEN
        FOREST

    BP Wood:
        Regional wood

    BP Wool:
        Faction color

    Front Door:
        South

TIER 2

    If chain is respected:

        Elven Town Forge

    If chain is ignored:

        Any valid Tier 2 BLACKSMITH Blueprint
        matching the consumer's selection criteria.

TIER 3

    A new Tier 3 Blueprint is selected inside
    the same 10x10 reserved site.

POLITICAL CHANGE

    Kingdom A
        |
        v
    Kingdom B

    BP Wool and BP Banner resolve to the
    new political affiliation.

======================================================================
40. EXAMPLE: PLAYER PLOT
======================================================================

PLAYER PLOT

    Plot Tier:
        2

    Area:
        Server-defined

    Maximum Height:
        Server-defined

    Mineral Rights:
        Server-defined

Player builds a house manually.

The Construction Mod does NOT:

    - Convert it into a Blueprint.
    - Assign it a Blueprint Tier.
    - Put it into the procedural Blueprint pool.
    - Recolor it because the town changed faction.
    - Require construction time.

Server-specific protection rules determine who can modify it.

======================================================================
41. EXAMPLE: NPC BAKERY
======================================================================

NPC requests a bakery
        |
        v
Maximum site reserved
        |
        v
Tier 1 Blueprint selected
        |
        v
Material reservoir created
        |
        v
NPC contributes materials
        |
        v
Players contribute materials
        |
        v
Materials reach 100%
        |
        v
Minimum construction time reached
        |
        v
Completed Construction
        |
        v
Bakery becomes available to other systems

======================================================================
42. WORLD EVOLUTION
======================================================================

The Construction Mod is intended to allow the world to become increasingly
shaped by player-created architecture without automatically allowing every
player building into procedural generation.

    Developer-authored Blueprints
              |
              v
    Players create new designs
              |
              v
    Blueprint review
              |
              v
    Approved Blueprint pool grows
              |
              v
    World Builder uses approved designs
              |
              v
    Future towns inherit player-created styles
              |
              v
    World becomes less generic over time

This is one of the major long-term goals of the system.

======================================================================
43. IMPLEMENTATION PRIORITIES
======================================================================

PHASE 1
-------
Core site reservation
One-anchor placement
Ghost preview
Site persistence
Basic construction flags

PHASE 2
-------
Blueprint data format
Exact block capture
Dimensions
Orientation
Front Door
Blueprint storage

PHASE 3
-------
City Planner
Structure Types
Tiers
Maximum reservations
Construction material reservoir

PHASE 4
-------
Construction timing
Player contributions
NPC contributions
Restart recovery
Completed Construction

PHASE 5
-------
BP material roles
Environmental resolution
Political resolution

PHASE 6
-------
Style tags
Blueprint approval
Blueprint library
Lineage
Evolution chains

PHASE 7
-------
Tier upgrades
Centered Tier replacement
Chain-respecting selection

PHASE 8
-------
World Builder integration
Cartography integration
Government integration
Economy integration
Quest integration
Protection hooks

PHASE 9
-------
Advanced connection points
Faction visual transitions
Advanced Blueprint selection
Additional BP material roles

======================================================================
44. OPEN DESIGN QUESTIONS
======================================================================

1. Exact City Planner progression.

2. Exact maximum reservations for each Structure Type.

3. Exact Structure Type registry.

4. Exact Tier definitions.

5. Exact even-dimension center convention.

6. Whether reserved sites may overlap under special circumstances.

7. How underground/vertical ghost previews should render.

8. Whether every Tier must exist in an Evolution Chain.

9. Whether Evolution Chains can skip Tiers.

10. How consumers choose among multiple valid Blueprints.

11. Exact Blueprint approval workflow.

12. Exact persistence split between world data and physical reservoirs.

13. Initial BP material-role registry.

14. Exact faction-change transition behavior.

15. Protection API.

16. Whether player plot expansion reserves future land immediately
    or expands the reservation when the plot Tier increases.

======================================================================
45. FINAL ARCHITECTURAL MODEL
======================================================================

The central model is:

                         BLUEPRINT
                             |
                             v
                   CONSTRUCTION PROJECT
                             |
                  materials + elapsed time
                             |
                             v
                  COMPLETED CONSTRUCTION
                             |
                  +----------+----------+
                  |                     |
                  v                     v
             TIER UPGRADE          POLITICAL CHANGE
                  |                     |
                  v                     v
            NEW BLUEPRINT         BP ROLE RESOLUTION
                  |                     |
                  +----------+----------+
                             |
                             v
                      CURRENT STRUCTURE

The reserved site is established at maximum size when the construction site
is created.

The current Tier determines the centered footprint.

The Blueprint determines the actual architecture.

A Completed Construction retains its identity through upgrades.

A Blueprint may optionally participate in an architectural evolution chain.

A Blueprint may contain variable material roles so that its architecture can
adapt to its environment or political context.

Player Plots remain completely separate from this system.

The Construction Mod itself remains neutral about whether a server behaves
like an MMORPG, survival server, creative server, PvP server, roleplaying
server, or something else.

It provides the construction capabilities.

The server ecosystem supplies the rules.
