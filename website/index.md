---
layout: default
title: IDEaz - The Post-Code IDE
---

<div class="hero">
    <h1>[oo] IDEaz</h1>
    <p class="tagline">Development that feels like it's just you and your ideas.</p>
    <p class="tagline secondary">The Post-Code IDE for Android.</p>
    <a href="https://github.com/HereLiesAz/IDEaz" class="cta-button">View on GitHub</a>
</div>

<div class="container">
    <h2>Not No-Code. Post-Code.</h2>
    <p>
        IDEaz is not a text editor; it is a <strong>visual creation engine</strong>. You interact primarily with your running application, not its source code.
        This isn't no-code. This isn't vibe coding. And this sure as hell ain't straight-up coding.
        This is what every emulator, visual preview, and drag-and-drop environment was leading up to.
    </p>

    <div class="feature-grid">
        <div class="feature-card">
            <h3>Invisible Overlay</h3>
            <p>
                A transparent layer over your live app. Tap to select elements, drag to group them. The interface fades away when you're interacting, and appears when you're creating.
            </p>
        </div>
        <div class="feature-card">
            <h3>Natural Language</h3>
            <p>
                Don't write XML or Kotlin. Just select a component and tell <strong>Jules</strong> (the AI) what you want. "Make this button blue," "Add a login screen here."
            </p>
        </div>
        <div class="feature-card">
            <h3>Race to Build</h3>
            <p>
                A dual-build strategy that races local compilation against remote cloud builds. Whichever finishes first is instantly installed on your device.
            </p>
        </div>
    </div>

    <h2>How It Works</h2>
    <p>
        The core loop of IDEaz is designed to keep you in the flow:
    </p>
    <ol class="workflow-list">
        <li><strong>Run App:</strong> Launch your project on the device.</li>
        <li><strong>Visual Select:</strong> Tap any UI element to inspect it.</li>
        <li><strong>AI Prompt:</strong> Describe your changes in plain English.</li>
        <li><strong>AI Edit:</strong> Jules accesses the source code and implements changes.</li>
        <li><strong>Compile & Run:</strong> The app rebuilds automatically.</li>
    </ol>

    <h2>Architecture</h2>
    <p>
        IDEaz leverages a unique "Repository-Less" architecture on the device. It connects directly to GitHub, treating the remote repository as the source of truth.
        The device handles the UI and lightweight logic, while heavy lifting is offloaded to the cloud.
    </p>

</div>
