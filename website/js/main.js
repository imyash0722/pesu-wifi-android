/**
 * PESU WiFi for Android - Landing Page Interactive Script
 * Lightweight, zero dependencies, privacy-friendly.
 */

document.addEventListener('DOMContentLoaded', () => {
  // Mockup showcase tabs
  const tabs = document.querySelectorAll('.showcase-tab');
  const mockupImg = document.getElementById('showcase-mockup-img');

  const mockupSources = {
    home: './assets/mockups/home-screen.svg',
    accounts: './assets/mockups/accounts-screen.svg',
    logs: './assets/mockups/logs-screen.svg'
  };

  const mockupAlts = {
    home: 'PESU WiFi Home Screen mockup showing connected status and keepalive active',
    accounts: 'PESU WiFi Multi-Account Manager mockup showing encrypted accounts',
    logs: 'PESU WiFi Telemetry and Live Logs mockup'
  };

  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      const target = tab.getAttribute('data-target');
      if (!target || !mockupSources[target]) return;

      // Update active tab state
      tabs.forEach(t => t.classList.remove('active'));
      tab.classList.add('active');

      // Update mockup display with smooth fade transition
      if (mockupImg) {
        mockupImg.style.opacity = '0';
        mockupImg.style.transform = 'scale(0.98)';
        setTimeout(() => {
          mockupImg.src = mockupSources[target];
          mockupImg.alt = mockupAlts[target];
          mockupImg.style.opacity = '1';
          mockupImg.style.transform = 'scale(1)';
        }, 150);
      }
    });
  });

  // Setup transition styling for smooth swap
  if (mockupImg) {
    mockupImg.style.transition = 'opacity 0.2s ease, transform 0.2s ease';
  }
});
