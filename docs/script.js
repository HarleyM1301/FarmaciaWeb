const menuButton = document.querySelector('.menu-toggle');
const menu = document.querySelector('#menu');

menuButton.addEventListener('click', () => {
  const isOpen = menuButton.getAttribute('aria-expanded') === 'true';
  menuButton.setAttribute('aria-expanded', String(!isOpen));
  menuButton.querySelector('.sr-only').textContent = isOpen ? 'Abrir menú' : 'Cerrar menú';
  menu.classList.toggle('is-open', !isOpen);
});

menu.addEventListener('click', (event) => {
  if (event.target.closest('a')) {
    menuButton.setAttribute('aria-expanded', 'false');
    menuButton.querySelector('.sr-only').textContent = 'Abrir menú';
    menu.classList.remove('is-open');
  }
});

document.querySelector('#year').textContent = new Date().getFullYear();
