🎵 Moodify — Your AI Mood Guide
Moodify is an interactive web application designed to help users find the perfect content (books or movies) based on their current emotional state. The project combines a modern HTML/CSS frontend with a Java-based backend powered by Artificial Intelligence.

🚀 Key Features
AI-Powered Recommendations: Analyzes user mood descriptions via the Groq API (Llama 3) to suggest relevant titles.

Dual Search Mode: Seamlessly toggle between book and movie recommendations.

Interactive UI: Beautifully designed cards with hover animations and smooth transitions.

Rich Details: Modal windows featuring cover art, plot summaries, and direct links to Google Books or IMDb.

Responsive Design: Fully optimized for both desktop and mobile devices.

🛠 Tech Stack
Frontend: HTML5, CSS3 (Flexbox/Grid), JavaScript (Fetch API).

Backend: Java, Maven, Javalin (Lightweight Web Framework).

Integrations: * Groq Cloud API — For intelligent response generation.

Google Books API — To fetch book metadata.

OMDb API — To search for movie data.

🔐 Security Best Practices
This project follows professional security standards:

No hardcoded API keys: All sensitive data is managed via environment variables (System.getenv).

Git Protection: Configuration files and local IDE settings are strictly ignored via .gitignore.

📦 How to Run Locally
Clone the repository: ```bash
git clone https://www.google.com/search?q=https://github.com/mazhenovnurzhan1606-code/Moodify.git

Configure Environment: Set your GROQ_API_KEY in your system environment variables or IDE launch settings.

Run the Backend: Execute the Main.java class.

Launch Frontend: Open index.html in any modern web browser.