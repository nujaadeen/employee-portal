"""Sample documents for the evals and for demos. Fictional Baracel facts, generated as PDFs (no binary files in git)."""

SAMPLES: dict[str, dict[str, list[str]]] = {
    "product": {
        "Lantern Pro user guide.pdf": [
            "Baracel Lantern Pro: user guide\n\nThe Lantern Pro is a rechargeable camping lantern with a warm and a cool light mode.\n"
            "Pairing: hold the power button for five seconds until the ring blinks blue, open the Baracel app, and tap Pair.\n"
            "The lantern pairs over Bluetooth 5.3 and keeps the pairing after a restart.",
            "Battery and charging\n\nA full charge takes three hours over USB-C. On the warm mode a full battery lasts 48 hours.\n"
            "On the brightest cool mode the battery lasts 9 hours. The battery can be replaced by any Baracel service point.\n"
            "Warranty: the Lantern Pro has a two year warranty that covers the battery and the electronics.",
        ],
        "Lantern Pro specifications.pdf": [
            "Lantern Pro specifications\n\nWeight: 420 grams. Height: 19 centimetres. Water resistance: IP67, it survives one metre of water for 30 minutes.\n"
            "Brightness: up to 1200 lumens. Colour temperature: 2700 K warm, 5000 K cool.\nBox contents: lantern, USB-C cable, hanging strap, quick start card.",
        ],
    },
    "brand": {
        "Baracel brand voice.pdf": [
            "Baracel brand voice\n\nWe write the way we talk: plain, warm and clear. We use short sentences and everyday words.\n"
            "We never use jargon, hype or exclamation marks in a row. We say what a product does, then why it matters.\n"
            "Do not write 'revolutionary' or 'game changing'. Do say 'lasts a full weekend on one charge'.",
        ],
        "Logo and colour rules.pdf": [
            "Logo and colour rules\n\nKeep clear space around the logo equal to the height of the letter B. Never stretch or recolour the logo.\n"
            "The brand colours are deep navy for backgrounds and one strong blue for actions. Use white text on navy.",
        ],
    },
    "campaign": {
        "Autumn campaign brief.pdf": [
            "Autumn campaign brief: Light up the long evenings\n\nCampaign runs from 1 October to 30 November. Audience: families and weekend campers.\n"
            "Key message: the Lantern Pro lasts a full weekend on one charge. Offer: free hanging strap with every lantern.\n"
            "Channels: LinkedIn for company news, Instagram for photos. Tone: warm, calm, practical.",
        ],
    },
    "engineering": {
        "Deployment runbook.pdf": [
            "Deployment runbook\n\nProduction deployments run from the main branch through the release pipeline. Deployments happen on weekdays before 15:00.\n"
            "Rotate the signing keys with the vault rotate command every ninety days. The on-call engineer approves every rollback.",
            "Incident handling\n\nPage the on-call engineer through the alert channel. Severity one incidents need a written review within five working days.\n"
            "Database migrations must be backwards compatible with the previous release.",
        ],
    },
}

# question, expected collection, expected source title
GOLDEN: list[tuple[str, str, str]] = [
    ("How do I pair the Lantern Pro with my phone?", "product", "Lantern Pro user guide.pdf"),
    ("How long does a full charge take and how long does it last?", "product", "Lantern Pro user guide.pdf"),
    ("What is the warranty on the Lantern Pro?", "product", "Lantern Pro user guide.pdf"),
    ("How much does the Lantern Pro weigh and is it waterproof?", "product", "Lantern Pro specifications.pdf"),
    ("What is in the box with the lantern?", "product", "Lantern Pro specifications.pdf"),
    ("What tone of voice should we use in our writing?", "brand", "Baracel brand voice.pdf"),
    ("Which words should we avoid, like revolutionary?", "brand", "Baracel brand voice.pdf"),
    ("How much clear space do we keep around the logo?", "brand", "Logo and colour rules.pdf"),
    ("When does the autumn campaign run and who is the audience?", "campaign", "Autumn campaign brief.pdf"),
    ("What offer comes with the lantern in the autumn campaign?", "campaign", "Autumn campaign brief.pdf"),
    ("How often do we rotate the signing keys?", "engineering", "Deployment runbook.pdf"),
    ("What do we do for a severity one incident?", "engineering", "Deployment runbook.pdf"),
]
