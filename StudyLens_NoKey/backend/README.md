# StudyLens secure backend

The APK contains no OpenAI API key.

Set the server environment variable:

`OPENAI_API_KEY=your_secret_key`

Then:

`npm install`
`npm start`

Deploy this server to a service that supports Node.js and HTTPS, then replace the
`backendUrl` in `MainActivity.kt` with the deployed `/analyze` endpoint.

For a public release, add authentication, rate limiting, quotas, abuse protection,
and server-side image-size limits. The backend still requires an OpenAI account/key;
the point is that the user never receives or enters that secret.
